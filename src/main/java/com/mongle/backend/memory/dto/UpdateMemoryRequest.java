package com.mongle.backend.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMemoryRequest(
        @NotBlank(message = "summary는 필수입니다")
        @Size(max = 300, message = "summary는 300자를 넘을 수 없습니다")
        String summary
) {
}
