package com.mongle.backend.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareSessionRepository extends JpaRepository<CareSession, String> {

    Optional<CareSession> findBySessionIdAndUserId(String sessionId, String userId);

    List<CareSession> findByStatusAndStartedAtBefore(SessionStatus status, Instant cutoff);
}
