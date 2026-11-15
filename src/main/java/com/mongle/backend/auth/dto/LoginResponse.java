package com.mongle.backend.auth.dto;

import java.time.Instant;

public record LoginResponse(String userId, String accessToken, Instant expiresAt, String name) {
}
