package com.mongle.backend.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터에 @CurrentUserId String userId 로 붙이면 SecurityContext(JWT)의
 * userId가 들어온다. 이게 userId를 얻는 유일한 방법이다 — 요청 바디·쿼리파라미터로 userId를
 * 받는 DTO 필드는 만들지 않는다 (명세서 1.3 규칙).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
