package com.mongle.backend.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 30분 TTL — MemoryPreviewCleanupScheduler가 배치로 하드 삭제한다 (명세서 4.4). */
@Entity
@Table(name = "memory_preview")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryPreview {

    @Id
    @Column(name = "preview_id", length = 36, nullable = false, updatable = false)
    private String previewId;

    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    @Column(name = "session_id", length = 36, nullable = false, updatable = false)
    private String sessionId;

    @Column(name = "draft_summary", length = 300, nullable = false)
    private String draftSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static MemoryPreview create(String userId, String sessionId, String draftSummary) {
        MemoryPreview preview = new MemoryPreview();
        preview.previewId = java.util.UUID.randomUUID().toString();
        preview.userId = userId;
        preview.sessionId = sessionId;
        preview.draftSummary = draftSummary;
        preview.createdAt = Instant.now();
        return preview;
    }

    public boolean isOwnedBy(String candidateUserId) {
        return this.userId.equals(candidateUserId);
    }

    public boolean isExpired(Instant now, int ttlMinutes) {
        return createdAt.plusSeconds(ttlMinutes * 60L).isBefore(now);
    }
}
