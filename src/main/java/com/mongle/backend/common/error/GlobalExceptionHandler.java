package com.mongle.backend.common.error;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 모든 엔드포인트가 API_명세서_v1 2.1의 에러 포맷을 그대로 쓰게 하는 단일 지점.
 * 컨트롤러·서비스에서 직접 try-catch로 ResponseEntity를 만들지 말고 ApiException만 던지면 된다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        if (e.getErrorCode() == ErrorCode.AI_UPSTREAM_ERROR) {
            // 외부 AI 업스트림 실패는 로그로 추적하되, reflections 엔드포인트 자체는
            // 명세서 4.3 규칙대로 500이 아니라 폴백 문구 + 200으로 응답해야 한다.
            // 이 분기로 들어온다는 건 폴백 처리가 안 된 다른 호출이라는 뜻이므로 반드시 로그를 남긴다.
            log.error("AI_UPSTREAM_ERROR: {}", e.getMessage());
        }
        ErrorResponse body = e.getField() == null
                ? ErrorResponse.of(e.getErrorCode(), e.getMessage())
                : ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getField());
        return ResponseEntity.status(e.getErrorCode().status()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldErrors().stream().findFirst();
        String field = fieldError.map(f -> f.getField()).orElse(null);
        String message = fieldError.map(f -> f.getDefaultMessage()).orElse("요청 형식이 올바르지 않습니다");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, message, field));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("예상 못 한 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요"));
    }
}
