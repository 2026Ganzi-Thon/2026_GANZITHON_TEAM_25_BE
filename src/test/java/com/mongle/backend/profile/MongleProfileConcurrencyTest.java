package com.mongle.backend.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongle.backend.profile.dto.MongleProfileResponse;
import com.mongle.backend.profile.dto.UpdateMongleProfileRequest;
import com.mongle.backend.support.AbstractIntegrationTest;
import com.mongle.backend.support.AuthTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** PreferenceConcurrencyTest와 같은 이유로, 최초 조회 동시 호출이 row를 1개만 만드는지 확인한다. */
@Import(AuthTestSupport.class)
class MongleProfileConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 5;

    @Autowired private MongleProfileService mongleProfileService;
    @Autowired private AuthTestSupport authTestSupport;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void 같은_유저의_최초_조회가_동시에_와도_예외없이_row가_1개만_생긴다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String userId = authTestSupport.newUser().userId();
                CountDownLatch ready = new CountDownLatch(THREADS);
                CountDownLatch start = new CountDownLatch(1);

                List<Future<MongleProfileResponse>> futures = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return mongleProfileService.get(userId);
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                for (Future<MongleProfileResponse> future : futures) {
                    MongleProfileResponse response = future.get(30, TimeUnit.SECONDS);
                    assertThat(response.onboardingCompleted()).isFalse();
                }

                Integer rows = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM mongle_profile WHERE user_id = ?", Integer.class, userId);
                assertThat(rows).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentPatchesToDifferentFieldsPreserveBothChanges() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < ROUNDS; round++) {
                String userId = authTestSupport.newUser().userId();
                mongleProfileService.get(userId);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<MongleProfileResponse> trait = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mongleProfileService.update(
                            userId, new UpdateMongleProfileRequest("잘 웃기", null, null));
                });
                Future<MongleProfileResponse> accessory = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mongleProfileService.update(
                            userId, new UpdateMongleProfileRequest(null, null, "모자"));
                });
                boolean bothReady = ready.await(10, TimeUnit.SECONDS);
                start.countDown();
                assertThat(bothReady).isTrue();
                trait.get(30, TimeUnit.SECONDS);
                accessory.get(30, TimeUnit.SECONDS);

                MongleProfileResponse result = mongleProfileService.get(userId);
                assertThat(result.similarityTrait()).isEqualTo("잘 웃기");
                assertThat(result.accessory()).isEqualTo("모자");
            }
        }
    }
}
