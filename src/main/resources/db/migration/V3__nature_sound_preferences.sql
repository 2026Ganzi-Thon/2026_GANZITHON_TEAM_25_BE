ALTER TABLE app_preference
    ADD COLUMN nature_sound VARCHAR(7) NOT NULL DEFAULT 'off',
    ADD COLUMN nature_sound_volume INT NOT NULL DEFAULT 50,
    ADD CONSTRAINT chk_app_preference_nature_sound
        CHECK (CAST(nature_sound AS BINARY) IN ('off', 'wind', 'insects', 'rain', 'waves')),
    ADD CONSTRAINT chk_app_preference_nature_sound_volume
        CHECK (nature_sound_volume BETWEEN 0 AND 100);
