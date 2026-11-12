package com.mongle.backend.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 현실 날씨를 반영한 배경 결정용. 사용자 위치를 받지 않는다(서버 설정의 고정 위치 — WeatherProperties). */
@RestController
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/weather")
    public ResponseEntity<WeatherResponse> current() {
        return ResponseEntity.ok(weatherService.current());
    }
}
