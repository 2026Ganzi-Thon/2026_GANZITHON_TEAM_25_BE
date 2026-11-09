package com.mongle.backend.session.dto;

import com.mongle.backend.session.SessionStatus;
import java.time.Instant;

public record FinishSessionResponse(String sessionId, SessionStatus status, Instant endedAt) {
}
