package com.mongle.backend.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongle.backend.weather.WeatherBackground.Condition;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WeatherBackgroundTest {

    private static final LocalDateTime SUNRISE = LocalDateTime.of(2026, 11, 12, 7, 8);
    private static final LocalDateTime SUNSET = LocalDateTime.of(2026, 11, 12, 17, 23);

    private WeatherBackground at(Condition condition, int hour, int minute) {
        return WeatherBackground.resolve(condition, LocalDateTime.of(2026, 11, 12, hour, minute), SUNRISE, SUNSET);
    }

    @Test
    void WMO_코드를_네_단계로_줄인다() {
        assertThat(WeatherBackground.conditionOf(0)).isEqualTo(Condition.CLEAR);
        assertThat(WeatherBackground.conditionOf(1)).isEqualTo(Condition.CLEAR);
        assertThat(WeatherBackground.conditionOf(2)).isEqualTo(Condition.CLEAR);
        assertThat(WeatherBackground.conditionOf(3)).isEqualTo(Condition.CLOUDY);
        assertThat(WeatherBackground.conditionOf(45)).isEqualTo(Condition.CLOUDY);
        assertThat(WeatherBackground.conditionOf(53)).isEqualTo(Condition.RAIN);
        assertThat(WeatherBackground.conditionOf(66)).isEqualTo(Condition.RAIN);
        assertThat(WeatherBackground.conditionOf(81)).isEqualTo(Condition.RAIN);
        assertThat(WeatherBackground.conditionOf(95)).isEqualTo(Condition.RAIN);
        assertThat(WeatherBackground.conditionOf(73)).isEqualTo(Condition.SNOW);
        assertThat(WeatherBackground.conditionOf(86)).isEqualTo(Condition.SNOW);
    }

    @Test
    void 눈과_비는_시간대보다_우선한다() {
        assertThat(at(Condition.SNOW, 12, 0)).isEqualTo(WeatherBackground.SNOWY);
        assertThat(at(Condition.RAIN, 23, 0)).isEqualTo(WeatherBackground.RAINY);
    }

    @Test
    void 흐림도_시간대보다_우선한다() {
        assertThat(at(Condition.CLOUDY, 12, 0)).isEqualTo(WeatherBackground.CLOUDY);
    }

    @Test
    void 맑으면_시간대로_낮_노을_밤을_가른다() {
        assertThat(at(Condition.CLEAR, 13, 0)).isEqualTo(WeatherBackground.NOON);
        assertThat(at(Condition.CLEAR, 16, 22)).isEqualTo(WeatherBackground.NOON);
        assertThat(at(Condition.CLEAR, 16, 23)).isEqualTo(WeatherBackground.SUNSET); // 일몰 60분 전 경계
        assertThat(at(Condition.CLEAR, 17, 50)).isEqualTo(WeatherBackground.SUNSET);
        assertThat(at(Condition.CLEAR, 17, 53)).isEqualTo(WeatherBackground.NIGHT);  // 일몰 30분 뒤 경계
        assertThat(at(Condition.CLEAR, 3, 0)).isEqualTo(WeatherBackground.NIGHT);
        assertThat(at(Condition.CLEAR, 7, 7)).isEqualTo(WeatherBackground.NIGHT);
        assertThat(at(Condition.CLEAR, 7, 8)).isEqualTo(WeatherBackground.NOON);
    }

    @Test
    void 날씨정보가_없을_때의_시각_근사() {
        assertThat(WeatherBackground.timeOfDayByHour(12)).isEqualTo(WeatherBackground.NOON);
        assertThat(WeatherBackground.timeOfDayByHour(17)).isEqualTo(WeatherBackground.SUNSET);
        assertThat(WeatherBackground.timeOfDayByHour(19)).isEqualTo(WeatherBackground.NIGHT);
        assertThat(WeatherBackground.timeOfDayByHour(5)).isEqualTo(WeatherBackground.NIGHT);
    }
}
