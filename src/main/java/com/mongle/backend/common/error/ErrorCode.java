package com.mongle.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * API_명세서_v1 2.1 "에러 응답 포맷"의 error 코드 표 그대로.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    AI_UPSTREAM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    // 명세서 2.1 표에는 없는 코드. reflections 쪽 실패는 ReflectionService가 전부 흡수해서
    // AI_UPSTREAM_ERROR가 컨트롤러까지 올라오는 일은 정상 흐름에선 없어야 한다 — 그 코드는
    // "Gemini 호출 자체가 실패했다"는 신호로만 남겨두고, 진짜 예상 못 한 서버 오류는 이걸로 구분한다.
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
