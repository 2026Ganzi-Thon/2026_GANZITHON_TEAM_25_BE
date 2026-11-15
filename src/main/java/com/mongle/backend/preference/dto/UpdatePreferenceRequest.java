package com.mongle.backend.preference.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongle.backend.common.error.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/** 부분 업데이트 — 넘기지 않은 필드는 null로 두면 기존 값 유지 (PATCH 시맨틱). */
public record UpdatePreferenceRequest(
        Boolean soundEnabled,
        Boolean hapticsEnabled,
        Boolean animationEnabled,
        @Pattern(regexp = "off|wind|insects|rain|waves", message = "지원하지 않는 자연음입니다")
        String natureSound,
        @Schema(type = "integer", minimum = "0", maximum = "100", example = "50")
        JsonNode natureSoundVolume) {

    public Integer validatedNatureSoundVolume() {
        if (natureSoundVolume == null || natureSoundVolume.isNull()) {
            return null;
        }
        // 숫자 문자열이나 소수가 정수로 변환되기 전에 JSON 토큰과 값 범위를 확인한다.
        if (!natureSoundVolume.isIntegralNumber() || !natureSoundVolume.canConvertToInt()
                || natureSoundVolume.intValue() < 0 || natureSoundVolume.intValue() > 100) {
            throw ApiException.validation("자연음 음량은 0~100 사이의 정수여야 합니다", "natureSoundVolume");
        }
        return natureSoundVolume.intValue();
    }
}
