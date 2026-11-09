package com.mongle.backend.common.security;

import com.mongle.backend.common.error.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && parameter.getParameterType().equals(String.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // 정상 흐름에선 SecurityConfig가 먼저 401을 돌려줘서 여기까지 안 온다.
            // 그래도 혹시 모를 경로(permitAll로 잘못 뚫린 엔드포인트 등)를 막는 2차 방어선.
            throw ApiException.unauthorized("인증 토큰이 없거나 유효하지 않습니다");
        }
        return authentication.getPrincipal();
    }
}
