package com.mongle.backend.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongle.backend.memory.ApprovedMemory;
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
 * "선택한 기억이 Gemini 프롬프트에 실제로 들어가는가"를 Gemini 호출 없이 고정한다.
 * 2026-11-15 실제 키로 확인해 보니 응답 문장만으로는 기억이 반영됐는지 판정할 수 없었고,
 * selectedMemoryIds를 검증하는 테스트도 없었다 — 그래서 모델에 보내는 프롬프트 문자열을 직접 잡아서 본다.
 */
class ReflectionServicePromptTest {

    private static final String USER_ID = "user-1";

    private GeminiClient geminiClient;
    private ApprovedMemoryRepository approvedMemoryRepository;
    private ReflectionService service;

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiClient.class);
        approvedMemoryRepository = mock(ApprovedMemoryRepository.class);
        service = new ReflectionService(
                mock(SessionService.class),
                approvedMemoryRepository,
                new SessionContextStore(),
                new RiskKeywordDetector(List.of("죽고싶")),
                geminiClient);
        ReflectionTestUtils.setField(service, "maxPerSession", 20);
        when(geminiClient.generateReflection(anyString(), anyString()))
                .thenReturn(new GeminiClient.ReflectionReply("마음이 속상했겠어요", false));
    }

    private String capturedPrompt() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateReflection(anyString(), prompt.capture());
        return prompt.getValue();
    }

    @Test
    void 선택한_기억은_승인된_요약_문장이_프롬프트에_들어간다() {
        ApprovedMemory memory = ApprovedMemory.approve(USER_ID, "친구가 약속을 자꾸 당일에 취소해서 서운했다");
        when(approvedMemoryRepository.findByUserIdAndMemoryIdIn(eq(USER_ID), anyList())).thenReturn(List.of(memory));

        service.reflect(USER_ID, "session-1",
                new ReflectionRequest("그 친구가 이번 주말에도 또 그랬어", List.of(memory.getMemoryId())));

        String prompt = capturedPrompt();
        assertThat(prompt).contains("사용자가 이전에 승인한 기억:");
        assertThat(prompt).contains("친구가 약속을 자꾸 당일에 취소해서 서운했다");
        assertThat(prompt).contains("그 친구가 이번 주말에도 또 그랬어");
    }

    @Test
    void 기억_조회는_토큰의_userId로만_한다_남의_기억_id를_보내도_그_유저_기준으로만_찾는다() {
        when(approvedMemoryRepository.findByUserIdAndMemoryIdIn(eq(USER_ID), anyList())).thenReturn(List.of());

        service.reflect(USER_ID, "session-1", new ReflectionRequest("안녕", List.of("someone-elses-memory-id")));

        verify(approvedMemoryRepository).findByUserIdAndMemoryIdIn(USER_ID, List.of("someone-elses-memory-id"));
        assertThat(capturedPrompt()).doesNotContain("사용자가 이전에 승인한 기억:");
    }

    @Test
    void 이번만_이야기하기는_기억을_조회하지도_프롬프트에_넣지도_않는다_빈_배열() {
        service.reflect(USER_ID, "session-1", new ReflectionRequest("오늘 좀 서운했어", List.of()));

        verify(approvedMemoryRepository, never()).findByUserIdAndMemoryIdIn(anyString(), anyList());
        assertThat(capturedPrompt()).doesNotContain("사용자가 이전에 승인한 기억:");
    }

    @Test
    void selectedMemoryIds를_아예_안_보내도_기억을_조회하지_않는다_null() {
        service.reflect(USER_ID, "session-1", new ReflectionRequest("오늘 좀 서운했어", null));

        verify(approvedMemoryRepository, never()).findByUserIdAndMemoryIdIn(anyString(), anyList());
        assertThat(capturedPrompt()).doesNotContain("사용자가 이전에 승인한 기억:");
    }
}
