package com.mongle.backend.auth;

import com.mongle.backend.auth.dto.LoginIdAvailabilityResponse;
import com.mongle.backend.auth.dto.LoginResponse;
import com.mongle.backend.auth.dto.SignupRequest;
import com.mongle.backend.auth.dto.SignupResponse;
import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.common.error.ErrorCode;
import com.mongle.backend.common.security.JwtTokenProvider;
import com.mongle.backend.profile.MongleProfileService;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이디·비밀번호 가입/로그인 (2026-11-15 팀 결정). 기존 익명(기기) 인증은 AuthService가 그대로 맡는다.
 *
 * 로그인 실패 응답은 "없는 아이디"와 "틀린 비밀번호"를 구분하지 않는다. 응답 문구뿐 아니라 걸리는 시간도 같아야
 * 계정 존재 여부를 알아낼 수 없어서, 없는 아이디일 때도 미리 만들어 둔 더미 해시와 비교한다.
 */
@Service
public class AccountService {

    static final String LOGIN_FAILED_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountRateLimiter rateLimiter;
    private final MongleProfileService mongleProfileService;
    private final String dummyHash;

    public AccountService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            AccountRateLimiter rateLimiter,
            MongleProfileService mongleProfileService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rateLimiter = rateLimiter;
        this.mongleProfileService = mongleProfileService;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional(readOnly = true)
    public LoginIdAvailabilityResponse checkLoginId(String loginId) {
        if (!AccountRules.isValidLoginId(loginId)) {
            throw ApiException.validation(AccountRules.LOGIN_ID_MESSAGE, "loginId");
        }
        return new LoginIdAvailabilityResponse(!appUserRepository.existsByLoginId(loginId));
    }

    /**
     * 아이디 중복은 미리 조회로 걸러내고, 거의 동시에 같은 아이디로 가입하는 경우는 DB의 UNIQUE 제약이 막는다.
     * 어느 쪽이든 409로 같은 응답을 준다(500이 되지 않게).
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (appUserRepository.existsByLoginId(request.loginId())) {
            throw duplicateLoginId();
        }
        AppUser user = AppUser.registerAccount(
                request.name().strip(), request.loginId(), passwordEncoder.encode(request.password()));
        try {
            appUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw duplicateLoginId();
        }
        return SignupResponse.justRegistered(user.getUserId());
    }

    @Transactional
    public LoginResponse login(String loginId, String password) {
        // 형식이 맞지 않는 아이디는 DB에 묻지 않는다. DB 비교는 대소문자·악센트를 무시해서("Abc123"·"àbc123"도 "abc123"과
        // 같다고 본다) 변형 문자열로 실패 횟수 제한을 우회할 수 있기 때문이다. 형식이 맞는 아이디만 소문자 영문·숫자이므로
        // 아래의 실패 횟수 키가 곧 계정 하나를 가리킨다.
        boolean wellFormed = AccountRules.isValidLoginId(loginId);
        if (wellFormed) {
            rateLimiter.assertNotBlocked(AccountRateLimiter.Scope.LOGIN_FAILURE, loginId);
        }

        AppUser user = wellFormed ? appUserRepository.findByLoginId(loginId).orElse(null) : null;
        String hash = (user != null && user.getPasswordHash() != null) ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(truncatedForBcrypt(password), hash);

        if (user == null || !passwordMatches || password.getBytes(StandardCharsets.UTF_8).length > AccountRules.BCRYPT_MAX_BYTES) {
            if (wellFormed) {
                rateLimiter.recordFailure(AccountRateLimiter.Scope.LOGIN_FAILURE, loginId);
            }
            throw ApiException.unauthorized(LOGIN_FAILED_MESSAGE);
        }

        rateLimiter.reset(AccountRateLimiter.Scope.LOGIN_FAILURE, loginId);
        user.touch();
        String token = jwtTokenProvider.generate(user.getUserId());
        boolean onboardingCompleted = mongleProfileService.isOnboardingCompleted(user.getUserId());
        return new LoginResponse(
                user.getUserId(), token, jwtTokenProvider.expirationFor(token), user.getName(), onboardingCompleted);
    }

    /** BCrypt는 72바이트 뒤를 무시하거나 구현에 따라 예외를 던진다 — 비교는 항상 안전한 길이로 하고, 초과 입력은 위에서 실패로 처리한다. */
    private static String truncatedForBcrypt(String password) {
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= AccountRules.BCRYPT_MAX_BYTES) {
            return password;
        }
        return "x".repeat(AccountRules.BCRYPT_MAX_BYTES); // 어차피 실패로 처리되므로 값은 중요하지 않다
    }

    private static ApiException duplicateLoginId() {
        return new ApiException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다", "loginId");
    }
}
