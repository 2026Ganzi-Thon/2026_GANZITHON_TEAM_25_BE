package com.mongle.backend.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongle.backend.auth.AccountRateLimiter.Scope;
import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 시계를 직접 움직여서 고정 창(window) 경계를 포함한 한도 동작을 서버 없이 확인한다. */
class AccountRateLimiterTest {

    /** 테스트가 시각을 앞으로 돌릴 수 있는 시계. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-15T00:00:00Z");

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MutableClock clock;
    private AccountRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        // signup 2, check-id 3, login-ip 4, login-failure 3, 창 10분
        limiter = new AccountRateLimiter(2, 3, 4, 3, 10, clock);
    }

    @Test
    void IP_범위는_한도까지_통과하고_넘으면_429다() {
        limiter.checkAndIncrement(Scope.SIGNUP_IP, "1.1.1.1");
        limiter.checkAndIncrement(Scope.SIGNUP_IP, "1.1.1.1");

        assertThatThrownBy(() -> limiter.checkAndIncrement(Scope.SIGNUP_IP, "1.1.1.1"))
                .isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
    }

    @Test
    void IP가_다르면_서로_영향을_주지_않는다() {
        limiter.checkAndIncrement(Scope.SIGNUP_IP, "1.1.1.1");
        limiter.checkAndIncrement(Scope.SIGNUP_IP, "1.1.1.1");

        assertThatCode(() -> limiter.checkAndIncrement(Scope.SIGNUP_IP, "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void 범위가_다르면_같은_IP여도_따로_센다() {
        limiter.checkAndIncrement(Scope.SIGNUP_IP, "1.1.1.1");
        limiter.checkAndIncrement(Scope.SIGNUP_IP, "1.1.1.1");

        assertThatCode(() -> limiter.checkAndIncrement(Scope.CHECK_ID_IP, "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void 창이_지나면_다시_처음부터_센다() {
        limiter.checkAndIncrement(Scope.SIGNUP_IP, "1.1.1.1");
        limiter.checkAndIncrement(Scope.SIGNUP_IP, "1.1.1.1");
        clock.advanceMinutes(10);

        assertThatCode(() -> limiter.checkAndIncrement(Scope.SIGNUP_IP, "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void 실패가_한도에_닿으면_그_아이디는_막히고_다른_아이디는_영향이_없다() {
        for (int i = 0; i < 3; i++) {
            limiter.assertNotBlocked(Scope.LOGIN_FAILURE, "abc123");
            limiter.recordFailure(Scope.LOGIN_FAILURE, "abc123");
        }

        assertThatThrownBy(() -> limiter.assertNotBlocked(Scope.LOGIN_FAILURE, "abc123"))
                .isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        assertThatCode(() -> limiter.assertNotBlocked(Scope.LOGIN_FAILURE, "other123")).doesNotThrowAnyException();
    }

    @Test
    void 실패_기록을_초기화하면_다시_시도할_수_있다() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure(Scope.LOGIN_FAILURE, "abc123");
        }
        limiter.reset(Scope.LOGIN_FAILURE, "abc123");

        assertThatCode(() -> limiter.assertNotBlocked(Scope.LOGIN_FAILURE, "abc123")).doesNotThrowAnyException();
    }

    @Test
    void 실패_차단도_창이_지나면_풀린다() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure(Scope.LOGIN_FAILURE, "abc123");
        }
        clock.advanceMinutes(10);

        assertThatCode(() -> limiter.assertNotBlocked(Scope.LOGIN_FAILURE, "abc123")).doesNotThrowAnyException();
    }

    @Test
    void 확인만_하는_assertNotBlocked는_횟수를_늘리지_않는다() {
        for (int i = 0; i < 20; i++) {
            limiter.assertNotBlocked(Scope.LOGIN_FAILURE, "abc123");
        }
        // 한도(3) 전이므로 여전히 실패를 2번 더 기록할 수 있어야 한다
        limiter.recordFailure(Scope.LOGIN_FAILURE, "abc123");
        limiter.recordFailure(Scope.LOGIN_FAILURE, "abc123");
        assertThatCode(() -> limiter.assertNotBlocked(Scope.LOGIN_FAILURE, "abc123")).doesNotThrowAnyException();
    }
}
