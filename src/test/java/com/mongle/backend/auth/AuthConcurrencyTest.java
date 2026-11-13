package com.mongle.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongle.backend.auth.dto.AnonymousAuthResponse;
import com.mongle.backend.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 기기에서 익명 인증이 거의 동시에 여러 번 와도(재부팅 직후 앱 중복 기동 등) 예외 없이
 * 전부 같은 userId를 받고 app_user row가 정확히 1개만 생기는지 확인한다.
 * 레이스는 타이밍 의존이라 매 라운드 새 deviceId로 여러 번 반복한다.
 * (IP당 rate limit은 컨트롤러 쪽이라 서비스를 직접 부르는 이 테스트엔 안 걸린다.)
 */
class AuthConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 5;

    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void 같은_기기의_익명인증이_동시에_와도_예외없이_같은_유저_1명만_생긴다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String deviceId = UUID.randomUUID().toString();
                CountDownLatch ready = new CountDownLatch(THREADS);
                CountDownLatch start = new CountDownLatch(1);

                List<Future<AnonymousAuthResponse>> futures = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return authService.authenticate(deviceId);
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                Set<String> userIds = new HashSet<>();
                for (Future<AnonymousAuthResponse> future : futures) {
                    // 스레드 안에서 터진 예외는 여기서 ExecutionException으로 올라와 테스트를 실패시킨다
                    userIds.add(future.get(30, TimeUnit.SECONDS).userId());
                }
                assertThat(userIds).hasSize(1);

                Integer rows = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM app_user WHERE device_id = ?", Integer.class, deviceId);
                assertThat(rows).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
