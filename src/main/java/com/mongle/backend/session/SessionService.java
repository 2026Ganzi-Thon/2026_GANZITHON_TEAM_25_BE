package com.mongle.backend.session;

import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.session.dto.FinishSessionResponse;
import com.mongle.backend.session.dto.StartSessionRequest;
import com.mongle.backend.session.dto.StartSessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final CareSessionRepository careSessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public StartSessionResponse start(String userId, StartSessionRequest request) {
        CareSession session = careSessionRepository.save(CareSession.start(userId, request.entryType()));
        return new StartSessionResponse(session.getSessionId(), session.getEntryType(), session.getStartedAt());
    }

    /**
     * idempotent: 이미 FINISHED/EXPIRED여도 에러 없이 현재 상태를 그대로 반환한다
     * (API_명세서_v1 4.1 — "뒤로가기+끝내기"를 동시에 눌러도 500 나면 안 됨).
     */
    @Transactional
    public FinishSessionResponse finish(String userId, String sessionId) {
        CareSession session = findOwned(userId, sessionId);
        session.finish();
        eventPublisher.publishEvent(new SessionFinishedEvent(sessionId));
        return new FinishSessionResponse(session.getSessionId(), session.getStatus(), session.getEndedAt());
    }

    /** reflections 엔드포인트(4.3)가 호출 전에 세션이 살아있는지 확인할 때 쓴다. */
    @Transactional(readOnly = true)
    public CareSession findOwnedActive(String userId, String sessionId) {
        CareSession session = findOwned(userId, sessionId);
        if (!session.isActive()) {
            throw ApiException.conflict("이미 종료된 세션입니다");
        }
        return session;
    }

    /**
     * 다른 모듈(memory 등)이 세션 엔티티에 의존하지 않고 소유권만 확인할 때 쓴다.
     * 상태(ACTIVE/FINISHED)는 보지 않는다 — 종료된 세션으로도 기억 미리보기를 만들 수 있어야 함.
     */
    @Transactional(readOnly = true)
    public void verifyOwned(String userId, String sessionId) {
        findOwned(userId, sessionId);
    }

    private CareSession findOwned(String userId, String sessionId) {
        CareSession session = careSessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("세션을 찾을 수 없습니다"));
        if (!session.isOwnedBy(userId)) {
            throw ApiException.forbidden("본인 세션이 아닙니다");
        }
        return session;
    }
}
