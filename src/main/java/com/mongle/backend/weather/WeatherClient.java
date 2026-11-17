package com.mongle.backend.weather;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Open-Meteo 현재 날씨 조회(키 불필요, 무료는 비상업 용도 — README 참고). 실패는 전부
 * WeatherUnavailableException으로 묶어서 호출부가 폴백하게 한다.
 */
@Slf4j
@Component
public class WeatherClient {

    public record Snapshot(int weatherCode, LocalDateTime sunrise, LocalDateTime sunset) {}

    public static class WeatherUnavailableException extends RuntimeException {
        public WeatherUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
        public WeatherUnavailableException(String message) {
            super(message);
        }
    }

    private final WeatherProperties properties;
    private final RestClient restClient;

    public WeatherClient(WeatherProperties properties) {
        this.properties = properties;
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(properties.getTimeoutMs()))
                .setResponseTimeout(Timeout.ofMilliseconds(properties.getTimeoutMs()))
                .build();
        var httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    public Snapshot fetch() {
        try {
            ApiResponse response = restClient.get()
                    .uri("/forecast?latitude={lat}&longitude={lon}&current=weather_code&daily=sunrise,sunset"
                            + "&timezone={tz}&forecast_days=1",
                            properties.getLatitude(), properties.getLongitude(), properties.getTimezone())
                    .retrieve()
                    .body(ApiResponse.class);
            return toSnapshot(response);
        } catch (RestClientException e) {
            log.warn("날씨 API 호출 실패: {}", e.getMessage());
            throw new WeatherUnavailableException("날씨 API 호출 실패", e);
        }
    }

    Snapshot toSnapshot(ApiResponse response) {
        if (response == null || response.current() == null || response.daily() == null
                || response.daily().sunrise() == null || response.daily().sunrise().isEmpty()
                || response.daily().sunset() == null || response.daily().sunset().isEmpty()) {
            throw new WeatherUnavailableException("날씨 API 응답 형식이 예상과 다릅니다");
        }
        try {
            return new Snapshot(
                    response.current().weatherCode(),
                    LocalDateTime.parse(response.daily().sunrise().get(0)),
                    LocalDateTime.parse(response.daily().sunset().get(0)));
        } catch (RuntimeException e) {
            throw new WeatherUnavailableException("날씨 API 응답을 해석하지 못했습니다", e);
        }
    }

    record ApiResponse(Current current, Daily daily) {
        record Current(@JsonProperty("weather_code") int weatherCode) {}
        record Daily(List<String> sunrise, List<String> sunset) {}
    }
}
