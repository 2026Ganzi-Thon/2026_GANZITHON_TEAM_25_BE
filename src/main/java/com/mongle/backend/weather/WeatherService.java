package com.mongle.backend.weather;

import com.mongle.backend.weather.WeatherBackground.Condition;
import com.mongle.backend.weather.WeatherClient.Snapshot;
import com.mongle.backend.weather.WeatherClient.WeatherUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 외부 날씨 API를 사용자 수와 무관하게 캐시 주기(기본 10분)당 한 번만 부른다. 위치가 서버 설정으로
 * 고정이라 캐시 키가 하나뿐이다. 단일 인스턴스 가정이라 메모리 캐시로 충분하다(9장 전제 참고).
 *
 * 외부 API가 실패해도 500을 내지 않는다 — 배경은 장식이라, 오래된 값(최대 3시간) → 시각 기반 근사
 * 순서로 항상 응답한다. 실패 직후엔 1분간 재시도하지 않아 느린 타임아웃이 요청마다 쌓이지 않게 한다.
 */
@Slf4j
@Service
public class WeatherService {

    private static final Duration MAX_STALE = Duration.ofHours(3);
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);

    private final WeatherClient client;
    private final Duration cacheTtl;
    private final ZoneId zone;
    private final Clock clock;

    private Snapshot cached;
    private Instant cachedAt;
    private Instant nextAttemptAt = Instant.MIN;

    @Autowired
    public WeatherService(WeatherClient client, WeatherProperties properties) {
        this(client, properties, Clock.systemUTC());
    }

    WeatherService(WeatherClient client, WeatherProperties properties, Clock clock) {
        this.client = client;
        this.cacheTtl = Duration.ofMinutes(properties.getCacheMinutes());
        this.zone = ZoneId.of(properties.getTimezone());
        this.clock = clock;
    }

    public synchronized WeatherResponse current() {
        Instant now = clock.instant();
        refreshIfNeeded(now);

        LocalDateTime local = LocalDateTime.ofInstant(now, zone);
        if (cached == null || cachedAt.plus(MAX_STALE).isBefore(now)) {
            return new WeatherResponse(WeatherBackground.timeOfDayByHour(local.getHour()), null);
        }
        Condition condition = WeatherBackground.conditionOf(cached.weatherCode());
        return new WeatherResponse(
                WeatherBackground.resolve(condition, local, cached.sunrise(), cached.sunset()), condition);
    }

    private void refreshIfNeeded(Instant now) {
        boolean fresh = cached != null && !cachedAt.plus(cacheTtl).isBefore(now);
        if (fresh || now.isBefore(nextAttemptAt)) {
            return;
        }
        try {
            cached = client.fetch();
            cachedAt = now;
        } catch (WeatherUnavailableException e) {
            nextAttemptAt = now.plus(RETRY_AFTER_FAILURE);
            log.warn("날씨 갱신 실패, {}초 뒤 재시도: {}", RETRY_AFTER_FAILURE.toSeconds(), e.getMessage());
        }
    }
}
