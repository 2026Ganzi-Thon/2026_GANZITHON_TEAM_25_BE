package com.mongle.backend.memory.dto;

import com.mongle.backend.memory.ApprovedMemory;
import java.time.Instant;

public record MemoryResponse(String memoryId, String summary, Instant approvedAt, Instant updatedAt) {

    public static MemoryResponse from(ApprovedMemory memory) {
        return new MemoryResponse(memory.getMemoryId(), memory.getApprovedSummary(),
                memory.getApprovedAt(), memory.getUpdatedAt());
    }
}
