package com.mongle.backend.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongle.backend.memory.ApprovedMemoryRepository;
import com.mongle.backend.reflection.dto.ReflectionRequest;
import com.mongle.backend.reflection.gemini.GeminiClient;
import com.mongle.backend.session.SessionFinishedEvent;
import com.mongle.backend.session.SessionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 2026-11-17 FE 연동 검수에서 발견: finish() 이후 늦게 도착한 Gemini 응답이 세션 메모리에
 * 턴을 다시 쌓던 레이스의 회귀 테스트. Gemini 응답이 돌아오는 바로 그 순간 세션이
 * finish되는, 가장 좁고 가장 위험한 타이밍을 그대로 재현한다.
 */
class ReflectionServiceFinishedSessionTest {

    @Test
    void 세션이_끝난_뒤_늦게_도착한_응답은_메모리에_턴을_남기지_않는다() {
        SessionContextStore contextStore = new SessionContextStore();
        GeminiClient geminiClient = mock(GeminiClient.class);
        String sessionId = "session-late-1";

        when(geminiClient.generateReflection(anyString(), anyString())).thenAnswer(invocation -> {
            contextStore.onSessionFinished(new SessionFinishedEvent(sessionId));
            return new GeminiClient.ReflectionReply("힘들었겠다", false);
        });

        ReflectionService service = new ReflectionService(
                mock(SessionService.class),
                mock(ApprovedMemoryRepository.class),
                contextStore,
                new RiskKeywordDetector(List.of(), List.of()),
                geminiClient);
        ReflectionTestUtils.setField(service, "maxPerSession", 20);

        service.reflect("user-1", sessionId, new ReflectionRequest("많이 서운했어", null));

        assertThat(contextStore.recentTurns(sessionId)).isEmpty();
    }

    @Test
    void 세션이_끝나지_않았으면_응답이_정상적으로_턴에_쌓인다() {
        SessionContextStore contextStore = new SessionContextStore();
        GeminiClient geminiClient = mock(GeminiClient.class);
        String sessionId = "session-active-1";

        when(geminiClient.generateReflection(anyString(), anyString()))
                .thenReturn(new GeminiClient.ReflectionReply("힘들었겠다", false));

        ReflectionService service = new ReflectionService(
                mock(SessionService.class),
                mock(ApprovedMemoryRepository.class),
                contextStore,
                new RiskKeywordDetector(List.of(), List.of()),
                geminiClient);
        ReflectionTestUtils.setField(service, "maxPerSession", 20);

        service.reflect("user-1", sessionId, new ReflectionRequest("많이 서운했어", null));

        assertThat(contextStore.recentTurns(sessionId)).hasSize(1);
    }
}
