package com.mongle.backend.auth;

import java.util.regex.Pattern;

/**
 * 아이디·비밀번호·이름 규칙의 단일 출처. DTO의 Bean Validation과 서비스의 중복확인 검증이 같은 값을 쓴다.
 * 최소 길이·문자 조합은 프론트(피그마 와이어프레임 최종)와 맞췄고, 최대 길이·허용 문자는 서버 기준이다.
 *
 * - 아이디: 영문 소문자와 숫자만, 둘 다 포함, 6~20자. 대문자를 허용하면 "Abc123"과 "abc123"이 서로 다른
 *   아이디로 갈라져 사칭하기 쉬워서 소문자만 받는다.
 * - 비밀번호: 영문·숫자·특수기호를 모두 포함, 6~64자, 공백·한글 불가(ASCII 출력 가능 문자만).
 *   BCrypt는 72바이트까지만 해시에 반영해서, 한글처럼 한 글자가 여러 바이트인 입력이 조용히 잘리는 걸 막는다.
 */
public final class AccountRules {

    public static final String LOGIN_ID_REGEX = "^(?=.*[a-z])(?=.*[0-9])[a-z0-9]{6,20}$";
    public static final String LOGIN_ID_MESSAGE = "아이디는 영문 소문자와 숫자를 모두 포함한 6~20자여야 합니다";

    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[!-/:-@\\[-`{-~])[!-~]{6,64}$";
    public static final String PASSWORD_MESSAGE =
            "비밀번호는 영문, 숫자, 특수기호를 모두 포함한 6~64자여야 하며 한글·공백은 쓸 수 없습니다";

    public static final String NAME_REGEX = "^[^\\p{Cntrl}]+$";
    public static final String NAME_MESSAGE = "이름은 1~20자여야 합니다";
    public static final int NAME_MAX_LENGTH = 20;

    /** BCrypt가 해시에 반영하는 최대 바이트 수. */
    public static final int BCRYPT_MAX_BYTES = 72;

    private static final Pattern LOGIN_ID = Pattern.compile(LOGIN_ID_REGEX);

    private AccountRules() {}

    public static boolean isValidLoginId(String loginId) {
        return loginId != null && LOGIN_ID.matcher(loginId).matches();
    }
}
