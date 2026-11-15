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
 * 몽글의 말투는 기획이 반말체로 정했다(2026-11-14 승훈님). 모델 응답이 실제로 반말인지는 실제 Gemini를 불러야 알 수 있어서
 * scripts/eval_quality.py로 따로 측정하고, 여기서는 그 규칙이 모델에 보내는 시스템 지시문에서 빠지지 않게만 고정한다.
 */
class ReflectionServiceToneTest {

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
                .thenReturn(new GeminiClient.ReflectionReply("마음이 속상했겠다", false));
    }

    @Test
    void 시스템_지시문에_반말체_규칙이_들어_있다() {
        service.reflect("user-1", "session-1", new ReflectionRequest("오늘 좀 서운했어", null));

        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateReflection(instruction.capture(), anyString());
        assertThat(instruction.getValue())
                .contains("친근한 반말체")
                .contains("존댓말은")
                .contains("위기 신호가 있을 때도");
    }
}
