package com.mongle.backend.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApproveMemoryRequest(
        @NotBlank(message = "previewId는 필수입니다") String previewId,
        @NotBlank(message = "finalSummary는 필수입니다")
        @Size(max = 300, message = "finalSummary는 300자를 넘을 수 없습니다")
        String finalSummary
) {
}
