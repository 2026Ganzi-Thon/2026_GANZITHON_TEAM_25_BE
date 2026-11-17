package com.mongle.backend.weather;

/** background는 항상 있다. condition은 외부 날씨를 전혀 못 얻었을 때만 null. */
public record WeatherResponse(WeatherBackground background, WeatherBackground.Condition condition) {
}
