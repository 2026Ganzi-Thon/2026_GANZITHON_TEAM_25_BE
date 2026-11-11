package com.mongle.backend.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongle.backend.support.AbstractIntegrationTest;
import com.mongle.backend.support.AuthTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** API_명세서_v1 4.1 — 지금까지 테스트가 없던 배치 스케줄러. 직접 @Scheduled 메서드를 호출해 검증한다. */
@Import(AuthTestSupport.class)
class SessionExpirySchedulerTest extends AbstractIntegrationTest {

    @Autowired private CareSessionRepository careSessionRepository;
    @Autowired private SessionExpiryScheduler scheduler;
    @Autowired private AuthTestSupport authTestSupport;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void 만료시간을_넘긴_ACTIVE_세션은_EXPIRED로_전환된다() {
        String userId = authTestSupport.newUser().userId();
        CareSession session = careSessionRepository.save(CareSession.start(userId, EntryType.TALK));
        backdateStartedAt(session.getSessionId(), Instant.now().minus(25, ChronoUnit.HOURS));

        scheduler.expireStaleSessions();

        CareSession reloaded = careSessionRepository.findById(session.getSessionId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SessionStatus.EXPIRED);
        assertThat(reloaded.getEndedAt()).isNotNull();
    }

    @Test
    void 만료시간_이내의_ACTIVE_세션은_그대로_유지된다() {
        String userId = authTestSupport.newUser().userId();
        CareSession session = careSessionRepository.save(CareSession.start(userId, EntryType.TALK));
        backdateStartedAt(session.getSessionId(), Instant.now().minus(1, ChronoUnit.HOURS));

        scheduler.expireStaleSessions();

        CareSession reloaded = careSessionRepository.findById(session.getSessionId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void 이미_FINISHED된_오래된_세션은_건드리지_않는다() {
        String userId = authTestSupport.newUser().userId();
        CareSession session = careSessionRepository.save(CareSession.start(userId, EntryType.TALK));
        session.finish();
        careSessionRepository.save(session);
        backdateStartedAt(session.getSessionId(), Instant.now().minus(48, ChronoUnit.HOURS));

        scheduler.expireStaleSessions();

        CareSession reloaded = careSessionRepository.findById(session.getSessionId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SessionStatus.FINISHED);
    }

    private void backdateStartedAt(String sessionId, Instant startedAt) {
        jdbcTemplate.update(
                "UPDATE care_session SET started_at = ? WHERE session_id = ?",
                Timestamp.from(startedAt), sessionId);
    }
}
