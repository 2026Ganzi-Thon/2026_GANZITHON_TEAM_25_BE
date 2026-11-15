package com.mongle.backend.auth.dto;

import com.mongle.backend.auth.AccountRules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 약관 두 개(이용약관·개인정보 처리방침)는 필수라 둘 다 true여야 하고,
 * 서버가 동의한 시각을 저장한다. 비밀번호는 이 DTO를 로그에 찍지 않도록 toString을 막아둔다.
 */
public record SignupRequest(
        @NotBlank(message = AccountRules.NAME_MESSAGE)
        @Size(max = AccountRules.NAME_MAX_LENGTH, message = AccountRules.NAME_MESSAGE)
        @Pattern(regexp = AccountRules.NAME_REGEX, message = AccountRules.NAME_MESSAGE)
        String name,

        @NotBlank(message = AccountRules.LOGIN_ID_MESSAGE)
        @Pattern(regexp = AccountRules.LOGIN_ID_REGEX, message = AccountRules.LOGIN_ID_MESSAGE)
        String loginId,

        @NotBlank(message = AccountRules.PASSWORD_MESSAGE)
        @Pattern(regexp = AccountRules.PASSWORD_REGEX, message = AccountRules.PASSWORD_MESSAGE)
        String password,

        @NotNull(message = "이용약관에 동의해야 합니다")
        @AssertTrue(message = "이용약관에 동의해야 합니다")
        Boolean termsAgreed,

        @NotNull(message = "개인정보 처리방침에 동의해야 합니다")
        @AssertTrue(message = "개인정보 처리방침에 동의해야 합니다")
        Boolean privacyAgreed
) {
    @Override
    public String toString() {
        return "SignupRequest[loginId=" + loginId + "]"; // 이름·비밀번호는 로그에 남기지 않는다
    }
}
