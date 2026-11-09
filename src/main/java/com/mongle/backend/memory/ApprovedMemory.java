package com.mongle.backend.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "approved_memory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovedMemory {

    @Id
    @Column(name = "memory_id", length = 36, nullable = false, updatable = false)
    private String memoryId;

    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    @Column(name = "approved_summary", length = 300, nullable = false)
    private String approvedSummary;

    @Column(name = "approved_at", nullable = false, updatable = false)
    private Instant approvedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ApprovedMemory approve(String userId, String summary) {
        ApprovedMemory memory = new ApprovedMemory();
        memory.memoryId = java.util.UUID.randomUUID().toString();
        memory.userId = userId;
        memory.approvedSummary = summary;
        Instant now = Instant.now();
        memory.approvedAt = now;
        memory.updatedAt = now;
        return memory;
    }

    public void editSummary(String summary) {
        this.approvedSummary = summary;
        this.updatedAt = Instant.now();
    }

    public boolean isOwnedBy(String candidateUserId) {
        return this.userId.equals(candidateUserId);
    }
}
