package com.mongle.backend.support;

import com.mongle.backend.auth.AuthService;
import com.mongle.backend.auth.dto.AnonymousAuthResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

/** 테스트에서 "로그인된 사용자"가 필요할 때마다 매번 anonymous auth를 거치지 않아도 되게 한다. */
@TestComponent
public class AuthTestSupport {

    @Autowired
    private AuthService authService;

    public AnonymousAuthResponse newUser() {
        return authService.authenticate(UUID.randomUUID().toString());
    }

    public String newUserToken() {
        return newUser().accessToken();
    }
}
