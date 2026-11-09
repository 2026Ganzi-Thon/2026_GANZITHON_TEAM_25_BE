package com.mongle.backend.preference.dto;

import com.mongle.backend.preference.AppPreference;

public record PreferenceResponse(boolean soundEnabled, boolean hapticsEnabled, boolean animationEnabled) {

    public static PreferenceResponse from(AppPreference preference) {
        return new PreferenceResponse(
                preference.isSoundEnabled(), preference.isHapticsEnabled(), preference.isAnimationEnabled());
    }
}
