package com.mongle.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청. 가입 때의 형식 규칙을 여기서 다시 검사하지 않는다 — 규칙이 바뀌어도 기존 계정이 로그인할 수 있어야 하고,
 * 형식이 틀렸다고 400을 돌려주면 "아이디는 맞는데 비밀번호가 틀렸다"와 응답이 갈려 계정 존재 여부가 드러난다.
 * 대신 비정상적으로 긴 입력만 크기로 막는다.
 */
public record LoginRequest(
        @NotBlank(message = "아이디를 입력해주세요")
        @Size(max = 64, message = "아이디를 입력해주세요")
        String loginId,

        @NotBlank(message = "비밀번호를 입력해주세요")
        @Size(max = 128, message = "비밀번호를 입력해주세요")
        String password
) {
    @Override
    public String toString() {
        return "LoginRequest[loginId=" + loginId + "]"; // 비밀번호는 로그에 남기지 않는다
    }
}
