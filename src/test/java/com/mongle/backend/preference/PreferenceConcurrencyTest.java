package com.mongle.backend.preference;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongle.backend.preference.dto.PreferenceResponse;
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

/**
 * 첫 GET /preferences가 동시에 여러 번 들어와도(앱 기동 직후 화면 여러 곳에서 동시에 조회 등)
 * user_id PK 중복 INSERT로 500이 나지 않고 row가 정확히 1개만 생기는지 확인한다.
 * 레이스는 타이밍 의존이라 한 번만 돌리면 우연히 통과할 수 있어서, 매 라운드 새 유저로 여러 번 반복한다.
 */
@Import(AuthTestSupport.class)
class PreferenceConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 5;

    @Autowired private PreferenceService preferenceService;
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

                List<Future<PreferenceResponse>> futures = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return preferenceService.get(userId);
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                for (Future<PreferenceResponse> future : futures) {
                    // 스레드 안에서 터진 예외는 여기서 ExecutionException으로 올라와 테스트를 실패시킨다
                    PreferenceResponse response = future.get(30, TimeUnit.SECONDS);
                    assertThat(response.soundEnabled()).isTrue();
                }

                Integer rows = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM app_preference WHERE user_id = ?", Integer.class, userId);
                assertThat(rows).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
