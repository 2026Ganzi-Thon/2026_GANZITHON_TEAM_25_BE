package com.mongle.backend.reflection;

import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.memory.ApprovedMemoryRepository;
import com.mongle.backend.reflection.dto.ReflectionRequest;
import com.mongle.backend.reflection.dto.ReflectionResponse;
import com.mongle.backend.reflection.gemini.GeminiCallException;
import com.mongle.backend.reflection.gemini.GeminiClient;
import com.mongle.backend.session.SessionService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionService {

    private static final String FALLBACK_REPLY = "지금은 대답이 잘 안 떠올라. 괜찮다면 잠시 후 다시 말해줄래?";

    private static final String SYSTEM_INSTRUCTION = """
            너는 '몽글' — 가까운 관계 갈등으로 마음이 복잡한 사용자 곁의, 사용자를 일부 닮았지만
            더 편안한 성향의 작은 양이다. 아래 원칙을 반드시 지켜서 한두 문장으로만 답한다.
            - 짧고 담백한 공감만 한다. 추가 질문이나 되묻기를 하지 않는다.
            - 진단하지 않는다 ("그건 OOO인 것 같아" 같은 말 금지).
            - 의존을 만들지 않는다 (몽글이 없으면 안 된다는 식의 표현 금지).
            - 누구의 잘잘못도 판단하지 않는다.
            - 위험한 관계에서 화해를 권유하지 않는다.
            - 위기 신호가 보이면 전문 도움을 먼저 안내하는 톤을 섞되, 과장하거나 겁주지 않는다.
            - 전화번호나 기관명을 절대 직접 말하지 않는다 ("1393", "상담 전화" 같은 구체적 번호·명칭
              금지). 정확한 연락처는 앱이 별도 화면으로 안내하므로, 너는 "도움을 받아봐" 정도의
              일반적인 톤까지만 담당한다.
            - 매 발화마다 자해·자살·폭력(피해)·스토킹·위협 같은 실제 위험 신호가 있는지 스스로
              판단한다. 관련 단어가 보인다는 이유만으로 위험으로 보지 않는다(예: "칼로리",
              "그게 맞고 틀리고"는 위험 신호가 아니다). 본인이 실제로 위험에 처했거나 자해·자살을
              암시하는 문맥인지로 판단한다.
            - 반드시 {"reply": "...", "riskDetected": true 또는 false} 형태의 JSON으로만 응답한다.
              reply에는 위 원칙을 지킨 답변 문장만, riskDetected에는 방금 내린 위험 판단을 담는다.
            """;

    private final SessionService sessionService;
    private final ApprovedMemoryRepository approvedMemoryRepository;
    private final SessionContextStore contextStore;
    private final RiskKeywordDetector riskKeywordDetector;
    private final GeminiClient geminiClient;

    @Value("${mongle.reflections.max-per-session}")
    private int maxPerSession;

    @Transactional(readOnly = true)
    public ReflectionResponse reflect(String userId, String sessionId, ReflectionRequest request) {
        // 1. 세션 존재·소유·ACTIVE 확인 (없으면 404, 남의 세션이면 403, 끝난 세션이면 409)
        sessionService.findOwnedActive(userId, sessionId);

        // 2. rate limit — 명세서 4.3.2
        if (contextStore.incrementAndGet(sessionId) > maxPerSession) {
            throw ApiException.rateLimited("이번 세션에서 공감 대화를 너무 많이 요청했습니다");
        }

        // 3. 위험 신호 키워드 판정 — 명세서 4.3.1. 단순 포함 매칭이라 "칼로리"/"그게 맞고" 같은
        // 오탐이 있어서(2026-11-11 팀 피드백), Gemini가 응답하면 Gemini의 문맥 판정을 최종값으로
        // 쓴다. 키워드는 Gemini를 못 쓸 때의 안전망으로만 남는다.
        boolean keywordRisky = riskKeywordDetector.matches(request.text());

        // 4~5. 선택된 기억 + 최근 턴을 컨텍스트로 프롬프트 구성
        String prompt = buildPrompt(userId, sessionId, request);

        String reply;
        boolean risky;
        try {
            GeminiClient.ReflectionReply geminiReply = geminiClient.generateReflection(SYSTEM_INSTRUCTION, prompt);
            reply = geminiReply.reply();
            risky = geminiReply.riskDetected();
            contextStore.appendTurn(sessionId, new SessionContextStore.Turn(request.text(), reply));
        } catch (GeminiCallException e) {
            // 5. 실패해도 500이 아니라 폴백 문구 + 200. 단, Gemini를 못 믿는 상황에서는 키워드
            // 판정을 그대로 신뢰한다 — 실패했다고 riskDetected를 false로 덮으면 위기 상황에서
            // 더 위험해지는 설계가 된다(명세서 원문 문구를 안전 쪽으로 보강한 지점, README의
            // "명세서 대비 의도적 보강" 참고).
            log.warn("세션 {} reflection 폴백 처리: {}", sessionId, e.getMessage());
            reply = FALLBACK_REPLY;
            risky = keywordRisky;
        }

        return risky
                ? ReflectionResponse.risky(reply, riskKeywordDetector.resources())
                : ReflectionResponse.safe(reply);
    }

    private String buildPrompt(String userId, String sessionId, ReflectionRequest request) {
        StringBuilder sb = new StringBuilder();

        List<String> memories = request.selectedMemoryIdsOrEmpty().isEmpty()
                ? List.of()
                : approvedMemoryRepository.findByUserIdAndMemoryIdIn(userId, request.selectedMemoryIdsOrEmpty())
                        .stream().map(m -> m.getApprovedSummary()).toList();
        if (!memories.isEmpty()) {
            sb.append("사용자가 이전에 승인한 기억:\n")
                    .append(memories.stream().collect(Collectors.joining("\n")))
                    .append("\n\n");
        }

        // recentTurns는 appendTurn이 성공한 턴만 쌓으므로 자기 자신(이번 입력)은 포함되지 않는다
        List<SessionContextStore.Turn> recent = contextStore.recentTurns(sessionId);
        if (!recent.isEmpty()) {
            sb.append("이번 세션의 최근 대화:\n");
            for (SessionContextStore.Turn turn : recent) {
                sb.append("사용자: ").append(turn.userText()).append("\n몽글: ").append(turn.reply()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("사용자: ").append(request.text());
        return sb.toString();
    }
}
