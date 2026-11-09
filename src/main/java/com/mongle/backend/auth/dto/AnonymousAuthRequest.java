package com.mongle.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AnonymousAuthRequest(
        @NotBlank(message = "deviceId는 필수입니다")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "deviceId는 UUID v4 형식이어야 합니다")
        String deviceId
) {
}
