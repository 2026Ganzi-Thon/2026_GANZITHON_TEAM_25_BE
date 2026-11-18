package com.mongle.backend.auth.dto;

import java.time.Instant;

/** onboardingCompleted는 GET /mongle-profile과 같은 규칙(MongleProfile.isOnboardingCompleted) — 로그인 직후 바로 분기할 수 있게. */
public record LoginResponse(String userId, String accessToken, Instant expiresAt, String name, boolean onboardingCompleted) {
}
