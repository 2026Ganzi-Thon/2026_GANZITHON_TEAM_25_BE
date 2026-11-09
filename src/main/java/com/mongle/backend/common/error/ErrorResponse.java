package com.mongle.backend.common.error;

/**
 * API_명세서_v1 2.1 에러 응답 포맷: { "error": "...", "message": "...", "field": "..." }
 * field는 값이 없으면 JSON에서 생략한다 (record의 null 필드는 Jackson 기본 설정상 포함되므로
 * GlobalExceptionHandler에서 null 여부에 따라 포함 여부를 결정하지 않고, 프런트가 field==null을
 * "필드 특정 없음"으로 해석하도록 명세서에 맞춰 항상 내려준다).
 */
public record ErrorResponse(String error, String message, String field) {

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), message, null);
    }

    public static ErrorResponse of(ErrorCode code, String message, String field) {
        return new ErrorResponse(code.name(), message, field);
    }
}
