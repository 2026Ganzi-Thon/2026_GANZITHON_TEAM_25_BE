package com.mongle.backend.weather;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 디자인 산출물의 배경 에셋 6장(noon·sunset·night·cloudy·rainy·snowy)과 1:1로 대응한다.
 * 우선순위: 눈/비 > 흐림 > 시간대(낮·노을·밤).
 */
public enum WeatherBackground {
    NOON, SUNSET, NIGHT, CLOUDY, RAINY, SNOWY;

    /** 일몰 한 시간 전부터 30분 뒤까지를 노을로 본다. 디자인 확인 전 임의값. */
    static final Duration SUNSET_BEFORE = Duration.ofMinutes(60);
    static final Duration SUNSET_AFTER = Duration.ofMinutes(30);

    public enum Condition { CLEAR, CLOUDY, RAIN, SNOW }

    /** WMO 날씨 코드(Open-Meteo)를 4단계로 줄인다. 2(부분 흐림)는 맑음으로 본다. */
    static Condition conditionOf(int wmoCode) {
        if ((wmoCode >= 71 && wmoCode <= 77) || wmoCode == 85 || wmoCode == 86) {
            return Condition.SNOW;
        }
        if ((wmoCode >= 51 && wmoCode <= 67) || (wmoCode >= 80 && wmoCode <= 82) || wmoCode >= 95) {
            return Condition.RAIN;
        }
        if (wmoCode == 3 || wmoCode == 45 || wmoCode == 48) {
            return Condition.CLOUDY;
        }
        return Condition.CLEAR;
    }

    static WeatherBackground resolve(Condition condition, LocalDateTime now, LocalDateTime sunrise, LocalDateTime sunset) {
        if (condition == Condition.SNOW) {
            return SNOWY;
        }
        if (condition == Condition.RAIN) {
            return RAINY;
        }
        if (condition == Condition.CLOUDY) {
            return CLOUDY;
        }
        return timeOfDay(now, sunrise, sunset);
    }

    static WeatherBackground timeOfDay(LocalDateTime now, LocalDateTime sunrise, LocalDateTime sunset) {
        if (!now.isBefore(sunset.minus(SUNSET_BEFORE)) && now.isBefore(sunset.plus(SUNSET_AFTER))) {
            return SUNSET;
        }
        if (now.isBefore(sunrise) || !now.isBefore(sunset.plus(SUNSET_AFTER))) {
            return NIGHT;
        }
        return NOON;
    }

    /** 일출·일몰 정보조차 없을 때(외부 API 실패 + 캐시 없음)의 시각 기반 근사. */
    static WeatherBackground timeOfDayByHour(int hour) {
        if (hour >= 17 && hour < 19) {
            return SUNSET;
        }
        return (hour >= 6 && hour < 17) ? NOON : NIGHT;
    }
}
