package com.mongle.backend.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongle.backend.memory.ApprovedMemoryRepository;
import com.mongle.backend.reflection.dto.ReflectionRequest;
import com.mongle.backend.reflection.gemini.GeminiClient;
import com.mongle.backend.session.SessionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 위험 판정 범위를 "가해자 입장"까지 넓히는 규칙은 승훈님이 2026-11-15에 이미 결정했다
 * (과거 PTSD 극복은 허용, 가해자 입장은 위험으로). 실제 판정이 맞는지는 Gemini를 불러야
 * 확인되므로 scripts/eval_risk.py --hard로 따로 측정하고, 여기서는 그 규칙이 시스템
 * 지시문에서 빠지지 않게만 고정한다.
 */
class ReflectionServicePerpetratorRiskTest {

    private GeminiClient geminiClient;
    private ReflectionService service;

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiClient.class);
        service = new ReflectionService(
                mock(SessionService.class),
                mock(ApprovedMemoryRepository.class),
                new SessionContextStore(),
                new RiskKeywordDetector(List.of("죽고싶")),
                geminiClient);
        ReflectionTestUtils.setField(service, "maxPerSession", 20);
        when(geminiClient.generateReflection(anyString(), anyString()))
                .thenReturn(new GeminiClient.ReflectionReply("마음이 복잡했겠다", false));
    }

    @Test
    void 시스템_지시문에_가해자_입장_위험_규칙이_들어_있다() {
        service.reflect("user-1", "session-1", new ReflectionRequest("오늘 좀 서운했어", null));

        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateReflection(instruction.capture(), anyString());
        assertThat(instruction.getValue())
                .contains("가한 쪽")
                .contains("해결된 과거 사건")
                .contains("지금도 이어지거나 반복되는지");
    }
}
