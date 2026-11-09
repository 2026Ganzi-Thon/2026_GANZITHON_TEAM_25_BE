package com.mongle.backend.session;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * API_명세서_v1 4.1 — "24시간 동안 finish 호출 없이 방치된 ACTIVE 세션은 배치 작업이 EXPIRED로
 * 전환 + 임시 컨텍스트 폐기". 단일 인스턴스 가정이라 분산 락 없이 돈다(9장 전제와 동일).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionExpiryScheduler {

    private final CareSessionRepository careSessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${mongle.session.expire-after-hours}")
    private int expireAfterHours;

    @Scheduled(fixedDelay = 15 * 60 * 1000) // 15분마다
    @Transactional
    public void expireStaleSessions() {
        Instant cutoff = Instant.now().minus(expireAfterHours, ChronoUnit.HOURS);
        List<CareSession> stale = careSessionRepository.findByStatusAndStartedAtBefore(SessionStatus.ACTIVE, cutoff);
        for (CareSession session : stale) {
            session.expire();
            eventPublisher.publishEvent(new SessionFinishedEvent(session.getSessionId()));
        }
        if (!stale.isEmpty()) {
            log.info("세션 {}건 EXPIRED 처리", stale.size());
        }
    }
}
