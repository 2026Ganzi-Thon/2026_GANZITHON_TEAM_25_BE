package com.mongle.backend.auth;

import com.mongle.backend.auth.dto.AnonymousAuthResponse;
import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final AppUserRegistrar appUserRegistrar;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AnonymousAuthResponse authenticate(String deviceId) {
        AppUser user = findOrCreate(deviceId);
        user.touch();
        String token = jwtTokenProvider.generate(user.getUserId());
        return new AnonymousAuthResponse(user.getUserId(), token, jwtTokenProvider.expirationFor(token));
    }

    /**
     * 같은 기기에서 거의 동시에 두 번 호출돼도(디바이스 재부팅 직후 앱이 중복 기동하는 경우 등)
     * device_id UNIQUE 제약이 둘 중 하나를 막아주고, 진 쪽은 다시 조회해 승자의 row를 쓴다.
     * 실제 등록 시도는 AppUserRegistrar(REQUIRES_NEW)에 맡긴다 — 이유는 그 클래스 주석 참고.
     */
    private AppUser findOrCreate(String deviceId) {
        return appUserRepository.findByDeviceId(deviceId)
                .orElseGet(() -> {
                    AppUser created = appUserRegistrar.registerOrNull(deviceId);
                    if (created != null) {
                        return created;
                    }
                    return appUserRepository.findByDeviceId(deviceId)
                            .orElseThrow(() -> ApiException.conflict("기기 등록 처리 중 충돌이 발생했습니다. 다시 시도해주세요"));
                });
    }
}
