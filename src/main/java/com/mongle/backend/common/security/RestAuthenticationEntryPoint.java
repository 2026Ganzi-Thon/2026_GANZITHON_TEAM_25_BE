package com.mongle.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongle.backend.common.error.ErrorCode;
import com.mongle.backend.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 인증 자체가 안 된 요청 → 401 + 명세서 2.1 포맷. GlobalExceptionHandler는 필터 단계 예외를 못 잡으므로 따로 둔다. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // 인코딩을 안 정하면 getWriter()가 ISO-8859-1로 써서 한글 message가 '?'로 깨진다
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.of(ErrorCode.UNAUTHORIZED, "인증 토큰이 없거나 유효하지 않습니다")));
    }
}
