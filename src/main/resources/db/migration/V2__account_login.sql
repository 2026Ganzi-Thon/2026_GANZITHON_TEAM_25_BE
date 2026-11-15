-- 아이디·비밀번호 로그인 추가 (2026-11-15 팀 결정: 소셜 로그인은 제외, 자체 가입/로그인).
-- 기존 익명(기기) 유저 row는 그대로 둔다 — 새 컬럼은 전부 NULL 허용이고, 가입 유저는 device_id가 없다.
-- MySQL의 UNIQUE는 NULL을 여러 개 허용하므로 uq_app_user_device_id 제약은 그대로 둔다.
ALTER TABLE app_user
    MODIFY device_id VARCHAR(64) NULL,
    ADD COLUMN login_id          VARCHAR(20)  NULL,
    ADD COLUMN password_hash     VARCHAR(100) NULL,
    ADD COLUMN name              VARCHAR(20)  NULL,
    ADD COLUMN terms_agreed_at   DATETIME     NULL,
    ADD COLUMN privacy_agreed_at DATETIME     NULL,
    ADD CONSTRAINT uq_app_user_login_id UNIQUE (login_id);
