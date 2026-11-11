package com.mongle.backend.auth;

import com.mongle.backend.common.error.ApiException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * /auth/anonymous는 로그인이 없어서 누구나 임의의 deviceId(UUID)를 계속 보낼 수 있다. 같은
 * deviceId 재사용은 AppUserRegistrar가 기존 row를 찾아 재사용해서 문제 없지만, 매번 다른
 * deviceId를 보내면 app_user row가 무제한 생성될 수 있다(2026-11-11 보안 점검에서 발견) — 그래서
 * IP당 호출 수를 고정 창(fixed window) 단위로 제한한다.
 *
 * SessionContextStore와 같은 이유로 단일 인스턴스 가정 하에 ConcurrentHashMap만 쓴다(9장
 * 전제 참고). 다만 reflection 쪽처럼 "세션 종료" 신호로 맵을 비울 수 있는 자연스러운 계기가 없어서,
 * IP별 엔트리는 영구히 쌓인다 — 해커톤 데모 규모(IP 수가 매우 적음)에서는 무시할 수준이지만,
 * 운영 규모로 키우면 TTL 캐시나 Redis로 바꿔야 한다.
 */
@Component
public class AnonymousAuthRateLimiter {

    private record Window(long windowKey, AtomicInteger count) {}

    private final Map<String, Window> windowsByClient = new ConcurrentHashMap<>();
    private final int maxPerWindow;
    private final int windowMinutes;

    public AnonymousAuthRateLimiter(
            @Value("${mongle.auth.anonymous-rate-limit.max-per-window}") int maxPerWindow,
            @Value("${mongle.auth.anonymous-rate-limit.window-minutes}") int windowMinutes) {
        this.maxPerWindow = maxPerWindow;
        this.windowMinutes = windowMinutes;
    }

    /** 호출 전에 미리 증가시키고 한도를 넘기면 던진다 — 레이스로 한도를 살짝 넘기지 않기 위함. */
    public synchronized void checkAndIncrement(String clientKey) {
        long currentWindowKey = Instant.now().getEpochSecond() / 60 / windowMinutes;
        Window window = windowsByClient.get(clientKey);
        if (window == null || window.windowKey() != currentWindowKey) {
            window = new Window(currentWindowKey, new AtomicInteger(0));
            windowsByClient.put(clientKey, window);
        }
        if (window.count().incrementAndGet() > maxPerWindow) {
            throw ApiException.rateLimited("요청이 너무 많습니다. 잠시 후 다시 시도해주세요");
        }
    }
}
