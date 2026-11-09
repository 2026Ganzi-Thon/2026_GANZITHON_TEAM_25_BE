package com.mongle.backend.reflection;

import com.mongle.backend.session.SessionFinishedEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 대화 턴과 reflection 호출 횟수를 세션 동안만 들고 있는 순수 서버 메모리 저장소. DB에는
 * 절대 쓰지 않는다(API_명세서_v1 "본문은 저장 안 함" 원칙). 단일 인스턴스 가정이라 분산 캐시
 * 없이 ConcurrentHashMap으로 충분하다(9장 "배포 전제" 참고) — 여러 인스턴스로 늘리는 순간
 * 이 클래스를 Redis 기반으로 바꿔야 한다.
 */
@Component
public class SessionContextStore {

    public record Turn(String userText, String reply) {}

    private static final int MAX_TURNS_KEPT = 3;

    private final Map<String, Deque<Turn>> turnsBySession = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> reflectionCountBySession = new ConcurrentHashMap<>();

    public List<Turn> recentTurns(String sessionId) {
        Deque<Turn> turns = turnsBySession.get(sessionId);
        return turns == null ? List.of() : List.copyOf(turns);
    }

    public synchronized void appendTurn(String sessionId, Turn turn) {
        Deque<Turn> turns = turnsBySession.computeIfAbsent(sessionId, id -> new ArrayDeque<>());
        turns.addLast(turn);
        while (turns.size() > MAX_TURNS_KEPT) {
            turns.removeFirst();
        }
    }

    /** 호출 전에 미리 증가시키고 한도 초과 여부를 돌려준다 — 레이스로 한도를 살짝 넘기지 않기 위함. */
    public int incrementAndGet(String sessionId) {
        return reflectionCountBySession.computeIfAbsent(sessionId, id -> new AtomicInteger(0)).incrementAndGet();
    }

    @EventListener
    public void onSessionFinished(SessionFinishedEvent event) {
        turnsBySession.remove(event.sessionId());
        reflectionCountBySession.remove(event.sessionId());
    }
}
