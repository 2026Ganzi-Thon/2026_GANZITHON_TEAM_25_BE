package com.mongle.backend.preference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_preference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppPreference {

    @Id
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    @Column(name = "sound_enabled", nullable = false)
    private boolean soundEnabled;

    @Column(name = "haptics_enabled", nullable = false)
    private boolean hapticsEnabled;

    @Column(name = "animation_enabled", nullable = false)
    private boolean animationEnabled;

    public static AppPreference defaultFor(String userId) {
        AppPreference preference = new AppPreference();
        preference.userId = userId;
        preference.soundEnabled = true;
        preference.hapticsEnabled = true;
        preference.animationEnabled = true;
        return preference;
    }

    public void applyPatch(Boolean soundEnabled, Boolean hapticsEnabled, Boolean animationEnabled) {
        if (soundEnabled != null) this.soundEnabled = soundEnabled;
        if (hapticsEnabled != null) this.hapticsEnabled = hapticsEnabled;
        if (animationEnabled != null) this.animationEnabled = animationEnabled;
    }
}
