package com.mongle.backend.reflection;

import com.mongle.backend.memory.MemorySummarizer;
import com.mongle.backend.reflection.gemini.GeminiCallException;
import com.mongle.backend.reflection.gemini.GeminiClient;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * memory 모듈의 MemorySummarizer 포트 구현. "POST /memories/preview"가 이걸 호출한다.
 * 실패해도 preview 자체는 항상 성공해야 데모가 끊기지 않으므로, Gemini가 안 되면 최근 사용자
 * 발화를 그대로 다듬어 쓰는 아주 단순한 폴백으로 떨어진다(의역이 아니라 원문 축약이라 품질은
 * 낮지만, 사용자가 어차피 승인 전에 직접 수정할 수 있어 치명적이지 않다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiMemorySummarizer implements MemorySummarizer {

    private static final String SYSTEM_INSTRUCTION = """
            아래 대화를 바탕으로, 사용자가 나중에 다시 읽었을 때 바로 이해할 수 있는 한 문장
            요약을 만든다. 30자 내외. 상대방의 실명이나 연락처 같은 식별정보는 포함하지 않는다.
            감정과 상황만 담백하게 담는다.
            """;

    private final SessionContextStore contextStore;
    private final GeminiClient geminiClient;

    @Override
    public String summarize(String sessionId) {
        List<SessionContextStore.Turn> turns = contextStore.recentTurns(sessionId);
        if (turns.isEmpty()) {
            return "오늘의 대화";
        }
        try {
            String prompt = turns.stream()
                    .map(SessionContextStore.Turn::userText)
                    .collect(Collectors.joining(" / "));
            return geminiClient.generate(SYSTEM_INSTRUCTION, prompt);
        } catch (GeminiCallException e) {
            log.warn("기억 요약 생성 실패, 원문 축약으로 폴백: {}", e.getMessage());
            String lastUserText = turns.get(turns.size() - 1).userText();
            return lastUserText.length() > 60 ? lastUserText.substring(0, 60) + "…" : lastUserText;
        }
    }
}
