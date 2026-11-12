package com.mongle.backend.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongle.backend.weather.WeatherBackground.Condition;
import com.mongle.backend.weather.WeatherClient.Snapshot;
import com.mongle.backend.weather.WeatherClient.WeatherUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WeatherServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Snapshot CLEAR = snapshot(0);

    private final AtomicReference<Instant> now = new AtomicReference<>();
    private WeatherClient client;
    private WeatherService service;

    private static Snapshot snapshot(int code) {
        return new Snapshot(code, LocalDateTime.of(2026, 11, 12, 7, 8), LocalDateTime.of(2026, 11, 12, 17, 23));
    }

    private void setSeoulTime(int hour, int minute) {
        now.set(LocalDateTime.of(2026, 11, 12, hour, minute).atZone(SEOUL).toInstant());
    }

    @BeforeEach
    void setUp() {
        client = mock(WeatherClient.class);
        setSeoulTime(13, 0);
        Clock movingClock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        service = new WeatherService(client, new WeatherProperties(), movingClock);
    }

    @Test
    void 캐시_주기_안에서는_외부_API를_한_번만_부른다() {
        when(client.fetch()).thenReturn(CLEAR);

        service.current();
        setSeoulTime(13, 9);
        WeatherResponse second = service.current();

        verify(client, times(1)).fetch();
        assertThat(second.background()).isEqualTo(WeatherBackground.NOON);
        assertThat(second.condition()).isEqualTo(Condition.CLEAR);
    }

    @Test
    void 캐시_주기가_지나면_다시_부른다() {
        when(client.fetch()).thenReturn(CLEAR, snapshot(61));

        service.current();
        setSeoulTime(13, 11);
        WeatherResponse refreshed = service.current();

        verify(client, times(2)).fetch();
        assertThat(refreshed.background()).isEqualTo(WeatherBackground.RAINY);
    }

    @Test
    void 캐시된_날씨라도_시각은_매_요청마다_다시_계산한다() {
        when(client.fetch()).thenReturn(CLEAR);

        assertThat(service.current().background()).isEqualTo(WeatherBackground.NOON);
        setSeoulTime(16, 30); // 캐시 TTL이 지났으므로 다시 받아오지만 같은 스냅샷 → 노을 구간 진입
        assertThat(service.current().background()).isEqualTo(WeatherBackground.SUNSET);
    }

    @Test
    void 외부_API가_실패하고_캐시도_없으면_시각_근사로_응답하고_condition은_null() {
        when(client.fetch()).thenThrow(new WeatherUnavailableException("down"));

        WeatherResponse response = service.current();

        assertThat(response.background()).isEqualTo(WeatherBackground.NOON);
        assertThat(response.condition()).isNull();
    }

    @Test
    void 실패한_직후엔_1분간_재시도하지_않는다() {
        when(client.fetch()).thenThrow(new WeatherUnavailableException("down"));

        service.current();
        setSeoulTime(13, 0);
        service.current();
        service.current();

        verify(client, times(1)).fetch();
    }

    @Test
    void 갱신에_실패하면_오래된_값을_최대_3시간까지_쓴다() {
        when(client.fetch()).thenReturn(snapshot(73)).thenThrow(new WeatherUnavailableException("down"));

        service.current();
        setSeoulTime(14, 0); // 1시간 경과: 갱신 시도 실패 → 이전 값(눈) 유지
        assertThat(service.current().background()).isEqualTo(WeatherBackground.SNOWY);

        setSeoulTime(16, 30); // 3시간 30분 경과: 너무 오래돼서 시각 근사로 전환(노을 전이라 낮)
        WeatherResponse tooOld = service.current();
        assertThat(tooOld.condition()).isNull();
    }
}
