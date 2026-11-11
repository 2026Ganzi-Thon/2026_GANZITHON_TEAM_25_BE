package com.mongle.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 2026-11-11 보안 점검에서 발견: /auth/anonymous는 로그인이 없어 임의의 deviceId를 계속 보내면
 * app_user row가 무제한 생성될 수 있었다. IP당 창(window) 단위 호출 제한으로 막는다.
 */
class AnonymousAuthRateLimiterTest {

    @Test
    void 한도_이내_호출은_통과한다() {
        AnonymousAuthRateLimiter limiter = new AnonymousAuthRateLimiter(3, 10);

        limiter.checkAndIncrement("127.0.0.1");
        limiter.checkAndIncrement("127.0.0.1");
        limiter.checkAndIncrement("127.0.0.1");
        // 위 3번까지는 예외 없이 통과해야 한다
    }

    @Test
    void 한도를_넘기면_RATE_LIMITED를_던진다() {
        AnonymousAuthRateLimiter limiter = new AnonymousAuthRateLimiter(3, 10);
        limiter.checkAndIncrement("127.0.0.1");
        limiter.checkAndIncrement("127.0.0.1");
        limiter.checkAndIncrement("127.0.0.1");

        assertThatThrownBy(() -> limiter.checkAndIncrement("127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void 다른_IP는_서로의_한도에_영향을_주지_않는다() {
        AnonymousAuthRateLimiter limiter = new AnonymousAuthRateLimiter(1, 10);

        limiter.checkAndIncrement("1.1.1.1");
        limiter.checkAndIncrement("2.2.2.2"); // 1.1.1.1이 이미 한도를 채웠어도 2.2.2.2는 영향 없음

        assertThatThrownBy(() -> limiter.checkAndIncrement("1.1.1.1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 한도가_큰_값이면_정상적인_반복_호출은_막히지_않는다() {
        AnonymousAuthRateLimiter limiter = new AnonymousAuthRateLimiter(30, 10);

        for (int i = 0; i < 30; i++) {
            limiter.checkAndIncrement("4.4.4.4");
        }

        assertThatThrownBy(() -> limiter.checkAndIncrement("4.4.4.4")).isInstanceOf(ApiException.class);
    }
}
