package com.mongle.backend.auth;

import com.mongle.backend.auth.dto.AnonymousAuthRequest;
import com.mongle.backend.auth.dto.AnonymousAuthResponse;
import com.mongle.backend.auth.dto.LoginIdAvailabilityResponse;
import com.mongle.backend.auth.dto.LoginRequest;
import com.mongle.backend.auth.dto.LoginResponse;
import com.mongle.backend.auth.dto.SignupRequest;
import com.mongle.backend.auth.dto.SignupResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API_명세서_v1 1.2 + 2026-11-15 로그인 추가(v2) — 인증 불필요 (SecurityConfig의 PUBLIC_PATHS에 등록돼 있음).
 * 프론트는 아이디·비밀번호 로그인을 쓰고, /auth/anonymous(기기 익명)는 더 이상 쓰지 않지만 서버에는 남겨둔다.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountService accountService;
    private final AnonymousAuthRateLimiter rateLimiter;
    private final AccountRateLimiter accountRateLimiter;

    @PostMapping("/auth/anonymous")
    public ResponseEntity<AnonymousAuthResponse> authenticate(
            @Valid @RequestBody AnonymousAuthRequest request, HttpServletRequest httpRequest) {
        rateLimiter.checkAndIncrement(httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.OK).body(authService.authenticate(request.deviceId()));
    }

    @GetMapping("/auth/check-id")
    public LoginIdAvailabilityResponse checkLoginId(
            @RequestParam(name = "loginId", required = false) String loginId, HttpServletRequest httpRequest) {
        accountRateLimiter.checkAndIncrement(AccountRateLimiter.Scope.CHECK_ID_IP, httpRequest.getRemoteAddr());
        return accountService.checkLoginId(loginId);
    }

    @PostMapping("/auth/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        accountRateLimiter.checkAndIncrement(AccountRateLimiter.Scope.SIGNUP_IP, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.signup(request));
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        accountRateLimiter.checkAndIncrement(AccountRateLimiter.Scope.LOGIN_IP, httpRequest.getRemoteAddr());
        return accountService.login(request.loginId(), request.password());
    }
}
