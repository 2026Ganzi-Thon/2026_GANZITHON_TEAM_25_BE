package com.mongle.backend.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongle.backend.memory.ApprovedMemoryRepository;
import com.mongle.backend.reflection.dto.ReflectionRequest;
import com.mongle.backend.reflection.dto.ReflectionResponse;
import com.mongle.backend.reflection.gemini.GeminiCallException;
import com.mongle.backend.reflection.gemini.GeminiClient;
import com.mongle.backend.session.SessionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 2026-11-11 팀 피드백("칼로리"/"그게 맞고" 같은 키워드 오탐) 반영: Gemini가 응답하면 Gemini의
 * 문맥 판정이 최종값이고, 키워드는 Gemini를 못 쓸 때의 안전망으로만 쓴다. 이 조합 규칙을
 * 실제 Gemini 호출 없이(키 불필요) 네 가지 경우로 고정한다.
 */
class ReflectionServiceRiskJudgmentTest {

    private GeminiClient geminiClient;
    private ReflectionService service;

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiClient.class);
        service = new ReflectionService(
                mock(SessionService.class),
                mock(ApprovedMemoryRepository.class),
                new SessionContextStore(),
                new RiskKeywordDetector(List.of("칼로", "맞고", "죽고싶")),
                geminiClient);
        ReflectionTestUtils.setField(service, "maxPerSession", 20);
    }

    private ReflectionResponse reflect(String text) {
        return service.reflect("user-1", "session-1", new ReflectionRequest(text, null));
    }

    @Test
    void 키워드는_걸리지만_Gemini가_위험아님으로_판정하면_위험으로_보지_않는다() {
        when(geminiClient.generateReflection(anyString(), anyString()))
                .thenReturn(new GeminiClient.ReflectionReply("다이어트 중이구나", false));

        ReflectionResponse response = reflect("오늘 칼로리 너무 높게 먹었어");

        assertThat(response.riskDetected()).isFalse();
        assertThat(response.reply()).isEqualTo("다이어트 중이구나");
    }

    @Test
    void 키워드엔_안_걸려도_Gemini가_위험으로_판정하면_위험으로_본다() {
        when(geminiClient.generateReflection(anyString(), anyString()))
                .thenReturn(new GeminiClient.ReflectionReply("많이 힘들었겠다", true));

        ReflectionResponse response = reflect("다 그만두고 조용히 없어지고 싶어");

        assertThat(response.riskDetected()).isTrue();
        assertThat(response.riskResources()).hasSize(3);
    }

    @Test
    void Gemini가_실패하면_키워드_판정을_그대로_신뢰한다_위험() {
        when(geminiClient.generateReflection(anyString(), anyString())).thenThrow(new GeminiCallException("timeout"));

        ReflectionResponse response = reflect("요즘 죽고싶다는 생각이 들어");

        assertThat(response.riskDetected()).isTrue();
    }

    @Test
    void Gemini가_실패하고_키워드도_안_걸리면_위험으로_보지_않는다() {
        when(geminiClient.generateReflection(anyString(), anyString())).thenThrow(new GeminiCallException("timeout"));

        ReflectionResponse response = reflect("친구가 내 말을 안 들어줘서 서운했어");

        assertThat(response.riskDetected()).isFalse();
    }
}
