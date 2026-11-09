package com.mongle.backend.memory.dto;

import java.time.Instant;

/** warning은 명세서 4.4 — 30개 초과 시 "memory_limit_near", 아니면 null(JSON 생략 없이 그대로 내려줌). */
public record ApproveMemoryResponse(String memoryId, String summary, Instant approvedAt, String warning) {
}
