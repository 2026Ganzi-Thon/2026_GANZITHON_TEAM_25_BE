package com.mongle.backend.auth.dto;

/**
 * 가입 응답에는 토큰이 없다 — 프론트가 가입 완료 후 첫 화면에서 로그인하는 흐름이다.
 * onboardingCompleted는 응답 구조를 로그인과 맞추기 위한 필드다. 가입 직후엔 프로필이 있을 수 없으므로 항상 false다(DB 조회 없음).
 */
public record SignupResponse(String userId, boolean onboardingCompleted) {

    public static SignupResponse justRegistered(String userId) {
        return new SignupResponse(userId, false);
    }
}
