package com.mongle.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongle.backend.auth.dto.SignupRequest;
import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.common.error.ErrorCode;
import com.mongle.backend.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 아이디로 가입 요청이 거의 동시에 여러 개 와도 정확히 1명만 가입되고, 나머지는 500이 아니라 409(CONFLICT)가 되는지 확인한다.
 * 아이디 중복 조회는 동시 요청이 서로의 가입을 보지 못해서 둘 다 통과할 수 있으므로, 실제로 막아주는 건 DB의 UNIQUE 제약이다.
 * 레이스는 타이밍 의존이라 매 라운드 새 아이디로 여러 번 반복한다(서비스를 직접 부르므로 IP 제한은 걸리지 않는다).
 */
class AccountConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 5;

    @Autowired private AccountService accountService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void 같은_아이디로_동시에_가입하면_1명만_성공하고_나머지는_409다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String loginId = "c1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                CountDownLatch ready = new CountDownLatch(THREADS);
                CountDownLatch start = new CountDownLatch(1);

                List<Future<String>> futures = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return accountService.signup(
                                new SignupRequest("동시가입", loginId, "abc123!x", true, true)).userId();
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                int success = 0;
                int conflict = 0;
                for (Future<String> future : futures) {
                    try {
                        future.get(60, TimeUnit.SECONDS);
                        success++;
                    } catch (ExecutionException e) {
                        // 409 말고 다른 예외(500이 될 것들)면 여기서 테스트가 실패한다
                        assertThat(e.getCause()).isInstanceOf(ApiException.class);
                        assertThat(((ApiException) e.getCause()).getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                        conflict++;
                    }
                }

                assertThat(success).as("라운드 %d 성공 수", round).isEqualTo(1);
                assertThat(conflict).as("라운드 %d 409 수", round).isEqualTo(THREADS - 1);
                Integer rows = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM app_user WHERE login_id = ?", Integer.class, loginId);
                assertThat(rows).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
