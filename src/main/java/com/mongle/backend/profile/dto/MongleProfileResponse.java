package com.mongle.backend.profile.dto;

import com.mongle.backend.profile.MongleProfile;

public record MongleProfileResponse(
        String similarityTrait,
        String comfortStyle,
        String accessory,
        boolean onboardingCompleted) {

    public static MongleProfileResponse from(MongleProfile profile) {
        return new MongleProfileResponse(
                profile.getSimilarityTrait(),
                profile.getComfortStyle(),
                profile.getAccessory(),
                profile.isOnboardingCompleted());
    }
}
