package com.mongle.backend.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 본문(사용자 입력·AI 응답)은 어떤 컬럼에도 없다 — ERD 설계 그대로, 의도된 누락. */
@Entity
@Table(name = "care_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareSession {

    @Id
    @Column(name = "session_id", length = 36, nullable = false, updatable = false)
    private String sessionId;

    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 10, nullable = false, updatable = false)
    private EntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public static CareSession start(String userId, EntryType entryType) {
        CareSession session = new CareSession();
        session.sessionId = java.util.UUID.randomUUID().toString();
        session.userId = userId;
        session.entryType = entryType;
        session.status = SessionStatus.ACTIVE;
        session.startedAt = Instant.now();
        return session;
    }

    /** 몇 번을 불러도 안전해야 한다(명세서 4.1) — 이미 끝난 세션이면 조용히 아무것도 안 한다. */
    public void finish() {
        if (status == SessionStatus.ACTIVE) {
            status = SessionStatus.FINISHED;
            endedAt = Instant.now();
        }
    }

    public void expire() {
        if (status == SessionStatus.ACTIVE) {
            status = SessionStatus.EXPIRED;
            endedAt = Instant.now();
        }
    }

    public boolean isOwnedBy(String candidateUserId) {
        return this.userId.equals(candidateUserId);
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }
}
