package com.mongle.backend.auth;

import com.mongle.backend.common.error.ApiException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 가입·로그인·아이디 중복확인의 호출 제한. 고정 창(fixed window) 방식이고 서버 메모리에만 있다
 * (AnonymousAuthRateLimiter·SessionContextStore와 같은 단일 인스턴스 가정 — 재시작하면 초기화된다).
 *
 * 범위(Scope)별로 서로 다른 열쇠와 한도를 쓴다.
 *  - SIGNUP_IP / CHECK_ID_IP / LOGIN_IP : 접속 IP당 호출 수. 같은 와이파이를 쓰는 기기가 많을 수 있어 넉넉하게 잡는다.
 *  - LOGIN_FAILURE : 아이디당 "로그인 실패" 수. 한도에 닿으면 올바른 비밀번호로도 창이 끝날 때까지 막는다(무차별 대입 방어).
 *    아이디가 없는 계정이든 있는 계정이든 같은 규칙으로 센다 — 응답 차이로 계정 존재 여부가 드러나지 않게 하기 위해서다.
 *    트레이드오프: 남의 아이디로 일부러 실패를 쌓아 그 계정의 로그인을 잠시 막을 수 있다(창이 끝나면 풀림).
 */
@Component
public class AccountRateLimiter {

    public enum Scope { SIGNUP_IP, CHECK_ID_IP, LOGIN_IP, LOGIN_FAILURE }

    private static final int PURGE_THRESHOLD = 10_000;

    private record Window(long windowKey, int count) {}

    private final Map<String, Window> windows = new HashMap<>(); // this 모니터로 보호
    private final Map<Scope, Integer> maxPerWindow = new EnumMap<>(Scope.class);
    private final int windowMinutes;
    private final Clock clock;

    @Autowired
    public AccountRateLimiter(
            @Value("${mongle.auth.account-rate-limit.signup-ip-per-window:30}") int signupIp,
            @Value("${mongle.auth.account-rate-limit.check-id-ip-per-window:120}") int checkIdIp,
            @Value("${mongle.auth.account-rate-limit.login-ip-per-window:60}") int loginIp,
            @Value("${mongle.auth.account-rate-limit.login-failure-per-id:10}") int loginFailure,
            @Value("${mongle.auth.account-rate-limit.window-minutes:10}") int windowMinutes) {
        this(signupIp, checkIdIp, loginIp, loginFailure, windowMinutes, Clock.systemUTC());
    }

    AccountRateLimiter(int signupIp, int checkIdIp, int loginIp, int loginFailure, int windowMinutes, Clock clock) {
        maxPerWindow.put(Scope.SIGNUP_IP, signupIp);
        maxPerWindow.put(Scope.CHECK_ID_IP, checkIdIp);
        maxPerWindow.put(Scope.LOGIN_IP, loginIp);
        maxPerWindow.put(Scope.LOGIN_FAILURE, loginFailure);
        this.windowMinutes = windowMinutes;
        this.clock = clock;
    }

    /** IP 범위용: 미리 증가시키고 한도를 넘기면 던진다(동시 요청이 한도를 살짝 넘기지 않게). */
    public synchronized void checkAndIncrement(Scope scope, String clientKey) {
        int count = increment(scope, clientKey);
        if (count > maxPerWindow.get(scope)) {
            throw tooMany();
        }
    }

    /** 실패 횟수 범위용: 이미 한도에 닿았으면 던진다. 증가시키지는 않는다. */
    public synchronized void assertNotBlocked(Scope scope, String key) {
        Window window = windows.get(mapKey(scope, key));
        if (window != null && window.windowKey() == currentWindowKey() && window.count() >= maxPerWindow.get(scope)) {
            throw tooMany();
        }
    }

    public synchronized void recordFailure(Scope scope, String key) {
        increment(scope, key);
    }

    public synchronized void reset(Scope scope, String key) {
        windows.remove(mapKey(scope, key));
    }

    private int increment(Scope scope, String key) {
        long current = currentWindowKey();
        if (windows.size() > PURGE_THRESHOLD) {
            windows.values().removeIf(w -> w.windowKey() != current); // 지난 창의 기록은 의미가 없으니 비운다
        }
        String mapKey = mapKey(scope, key);
        Window window = windows.get(mapKey);
        int next = (window == null || window.windowKey() != current) ? 1 : window.count() + 1;
        windows.put(mapKey, new Window(current, next));
        return next;
    }

    private long currentWindowKey() {
        return clock.instant().getEpochSecond() / 60 / windowMinutes;
    }

    private static String mapKey(Scope scope, String key) {
        return scope.name() + ":" + key;
    }

    private static ApiException tooMany() {
        return ApiException.rateLimited("요청이 너무 많습니다. 잠시 후 다시 시도해주세요");
    }
}
