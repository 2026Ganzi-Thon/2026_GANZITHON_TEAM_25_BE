package com.mongle.backend.auth.dto;

/** 가입 응답에는 토큰이 없다 — 프론트가 가입 완료 후 첫 화면에서 로그인하는 흐름이다. */
public record SignupResponse(String userId) {
}
