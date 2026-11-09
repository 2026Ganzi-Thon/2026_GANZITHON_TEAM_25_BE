-- Mongle 초기 스키마 — API_명세서_v1 3장 ERD 그대로.
-- entry_type/status는 MySQL ENUM 대신 VARCHAR+CHECK로 둔다: H2(MySQL 모드) 테스트와
-- MySQL 운영 양쪽에서 동일하게 동작시키기 위한 선택이고, JPA에서는 EnumType.STRING으로 매핑된다.

CREATE TABLE app_user (
    user_id        VARCHAR(36)     NOT NULL,
    device_id      VARCHAR(64)  NOT NULL,
    created_at     DATETIME     NOT NULL,
    last_active_at DATETIME     NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uq_app_user_device_id UNIQUE (device_id)
);

CREATE TABLE app_preference (
    user_id           VARCHAR(36) NOT NULL,
    sound_enabled     BOOLEAN  NOT NULL DEFAULT TRUE,
    haptics_enabled   BOOLEAN  NOT NULL DEFAULT TRUE,
    animation_enabled BOOLEAN  NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_app_preference_user FOREIGN KEY (user_id) REFERENCES app_user (user_id)
);

CREATE TABLE care_session (
    session_id VARCHAR(36)    NOT NULL,
    user_id    VARCHAR(36)    NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    status     VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    started_at DATETIME    NOT NULL,
    ended_at   DATETIME    NULL,
    PRIMARY KEY (session_id),
    CONSTRAINT fk_care_session_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_care_session_entry_type CHECK (entry_type IN ('TALK', 'SILENT')),
    CONSTRAINT ck_care_session_status CHECK (status IN ('ACTIVE', 'FINISHED', 'EXPIRED'))
);
CREATE INDEX idx_care_session_user_id ON care_session (user_id);

-- 임시 테이블: preview 생성 후 30분 지나면 배치로 하드 삭제 (MemoryPreviewCleanupScheduler)
CREATE TABLE memory_preview (
    preview_id    VARCHAR(36)     NOT NULL,
    user_id       VARCHAR(36)     NOT NULL,
    session_id    VARCHAR(36)     NOT NULL,
    draft_summary VARCHAR(300) NOT NULL,
    created_at    DATETIME     NOT NULL,
    PRIMARY KEY (preview_id)
);
CREATE INDEX idx_memory_preview_user_id ON memory_preview (user_id);

CREATE TABLE approved_memory (
    memory_id        VARCHAR(36)     NOT NULL,
    user_id          VARCHAR(36)     NOT NULL,
    approved_summary VARCHAR(300) NOT NULL,
    approved_at      DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    PRIMARY KEY (memory_id),
    CONSTRAINT fk_approved_memory_user FOREIGN KEY (user_id) REFERENCES app_user (user_id)
);
CREATE INDEX idx_approved_memory_user_id ON approved_memory (user_id);

-- P1 (이번 주 범위 아님) — 테이블만 선점
CREATE TABLE mongle_profile (
    user_id          VARCHAR(36)    NOT NULL,
    similarity_trait VARCHAR(50) NULL,
    comfort_style    VARCHAR(50) NULL,
    fleece_color     VARCHAR(30) NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_mongle_profile_user FOREIGN KEY (user_id) REFERENCES app_user (user_id)
);
