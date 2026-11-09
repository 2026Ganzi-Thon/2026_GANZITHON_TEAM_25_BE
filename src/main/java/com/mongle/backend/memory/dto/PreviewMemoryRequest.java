package com.mongle.backend.memory.dto;

import jakarta.validation.constraints.NotBlank;

public record PreviewMemoryRequest(@NotBlank(message = "sessionId는 필수입니다") String sessionId) {
}
