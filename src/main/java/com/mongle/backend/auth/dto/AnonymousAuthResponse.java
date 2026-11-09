package com.mongle.backend.auth.dto;

import java.time.Instant;

public record AnonymousAuthResponse(String userId, String accessToken, Instant expiresAt) {
}
