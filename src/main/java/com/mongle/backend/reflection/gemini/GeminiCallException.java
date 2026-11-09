package com.mongle.backend.reflection.gemini;

/**
 * Gemini 호출 실패(키 미설정, 타임아웃, 4xx/5xx, 응답 파싱 실패)를 모두 하나로 묶는다.
 * ReflectionService가 이걸 잡아서 폴백 문구로 바꾼다 — 절대 컨트롤러까지 그대로 올려서
 * 500을 사용자에게 보여주지 않는다 (API_명세서_v1 4.3 처리 순서 5번).
 */
public class GeminiCallException extends RuntimeException {
    public GeminiCallException(String message, Throwable cause) {
        super(message, cause);
    }

    public GeminiCallException(String message) {
        super(message);
    }
}
