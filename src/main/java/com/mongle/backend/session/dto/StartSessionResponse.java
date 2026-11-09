package com.mongle.backend.session.dto;

import com.mongle.backend.session.EntryType;
import java.time.Instant;

public record StartSessionResponse(String sessionId, EntryType entryType, Instant startedAt) {
}
