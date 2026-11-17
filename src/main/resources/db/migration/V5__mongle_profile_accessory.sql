-- fleece_color를 accessory로 이름 변경 (2026-11-18, 형진님 11/15 온보딩 설계 — 장식 선택).
ALTER TABLE mongle_profile
    CHANGE COLUMN fleece_color accessory VARCHAR(30) NULL;
