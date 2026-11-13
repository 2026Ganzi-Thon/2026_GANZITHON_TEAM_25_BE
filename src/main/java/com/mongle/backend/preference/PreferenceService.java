package com.mongle.backend.preference;

import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.preference.dto.PreferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final AppPreferenceRepository appPreferenceRepository;
    private final AppPreferenceRegistrar appPreferenceRegistrar;

    @Transactional
    public PreferenceResponse get(String userId) {
        return PreferenceResponse.from(findOrCreateDefault(userId));
    }

    @Transactional
    public PreferenceResponse update(String userId, Boolean soundEnabled, Boolean hapticsEnabled, Boolean animationEnabled) {
        AppPreference preference = findOrCreateDefault(userId);
        preference.applyPatch(soundEnabled, hapticsEnabled, animationEnabled);
        return PreferenceResponse.from(preference);
    }

    /**
     * 첫 조회가 동시에 여러 번 와도 user_id PK가 하나만 통과시키고, 나머지는 승자의 row를 쓴다.
     * 실제 생성 시도는 AppPreferenceRegistrar(REQUIRES_NEW)에 맡긴다 — 이유는 그 클래스 주석 참고.
     *
     * 생성 후 다시 읽을 때 일반 findById가 아니라 잠금 읽기(findForUpdate)를 쓰는 이유: MySQL 기본
     * 격리수준(REPEATABLE READ)에서는 이 트랜잭션의 첫 SELECT 시점 스냅샷으로 계속 읽기 때문에,
     * 그 뒤 다른 트랜잭션(REQUIRES_NEW 포함)이 커밋한 row가 일반 SELECT로는 안 보인다. 잠금 읽기는
     * 최신 커밋 버전을 읽고, 결과가 이 트랜잭션의 영속 상태가 돼서 update()의 변경도 반영된다.
     */
    private AppPreference findOrCreateDefault(String userId) {
        return appPreferenceRepository.findById(userId)
                .orElseGet(() -> {
                    appPreferenceRegistrar.createDefaultIfAbsent(userId);
                    return appPreferenceRepository.findForUpdate(userId)
                            .orElseThrow(() -> ApiException.conflict("환경설정 생성 중 충돌이 발생했습니다. 다시 시도해주세요"));
                });
    }
}
