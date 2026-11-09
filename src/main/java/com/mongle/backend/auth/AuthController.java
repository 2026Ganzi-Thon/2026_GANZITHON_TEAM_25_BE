package com.mongle.backend.auth;

import com.mongle.backend.auth.dto.AnonymousAuthRequest;
import com.mongle.backend.auth.dto.AnonymousAuthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API_명세서_v1 1.2 — 인증 불필요 (SecurityConfig의 PUBLIC_PATHS에 등록돼 있음). */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/anonymous")
    public ResponseEntity<AnonymousAuthResponse> authenticate(@Valid @RequestBody AnonymousAuthRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(authService.authenticate(request.deviceId()));
    }
}
