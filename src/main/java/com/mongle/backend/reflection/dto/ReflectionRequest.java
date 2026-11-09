package com.mongle.backend.reflection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReflectionRequest(
        @NotBlank(message = "text는 필수입니다")
        @Size(max = 500, message = "text는 500자를 넘을 수 없습니다")
        String text,
        List<String> selectedMemoryIds
) {
    public List<String> selectedMemoryIdsOrEmpty() {
        return selectedMemoryIds == null ? List.of() : selectedMemoryIds;
    }
}
