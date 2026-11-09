package com.mongle.backend.preference.dto;

/** 부분 업데이트 — 넘기지 않은 필드는 null로 두면 기존 값 유지 (PATCH 시맨틱). */
public record UpdatePreferenceRequest(Boolean soundEnabled, Boolean hapticsEnabled, Boolean animationEnabled) {
}
