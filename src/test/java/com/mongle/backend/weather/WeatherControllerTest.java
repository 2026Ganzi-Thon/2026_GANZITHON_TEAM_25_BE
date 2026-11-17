package com.mongle.backend.weather;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mongle.backend.support.AbstractIntegrationTest;
import com.mongle.backend.support.AuthTestSupport;
import com.mongle.backend.weather.WeatherClient.Snapshot;
import com.mongle.backend.weather.WeatherClient.WeatherUnavailableException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** 외부 Open-Meteo는 목으로 막는다 — 실제 호출 검증은 PR에 기록한 서버 수동 확인으로 대신한다. */
@Import(AuthTestSupport.class)
class WeatherControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthTestSupport authTestSupport;
    @Autowired private WeatherService weatherService;
    @MockitoBean private WeatherClient weatherClient;

    // 서비스는 캐시·실패 후 재시도 유예 상태를 들고 있는 싱글톤이라, 앞 테스트의 실패가 다음 테스트로 새지 않게 비운다.
    @BeforeEach
    void resetWeatherServiceState() {
        ReflectionTestUtils.setField(weatherService, "cached", null);
        ReflectionTestUtils.setField(weatherService, "cachedAt", null);
        ReflectionTestUtils.setField(weatherService, "nextAttemptAt", Instant.MIN);
    }

    @Test
    void 토큰_없이_조회하면_401() throws Exception {
        mockMvc.perform(get("/weather"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void 외부_API가_죽어도_200으로_배경을_돌려준다() throws Exception {
        when(weatherClient.fetch()).thenThrow(new WeatherUnavailableException("down"));
        String token = authTestSupport.newUserToken();

        mockMvc.perform(get("/weather").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.background").isString())
                .andExpect(jsonPath("$.condition").doesNotExist());
    }

    @Test
    void 눈이_오면_SNOWY와_SNOW를_돌려준다() throws Exception {
        when(weatherClient.fetch()).thenReturn(new Snapshot(
                73, LocalDateTime.of(2026, 11, 12, 7, 8), LocalDateTime.of(2026, 11, 12, 17, 23)));
        String token = authTestSupport.newUserToken();

        mockMvc.perform(get("/weather").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.background").value("SNOWY"))
                .andExpect(jsonPath("$.condition").value("SNOW"));
    }
}
