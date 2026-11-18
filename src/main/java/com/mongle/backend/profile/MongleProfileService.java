package com.mongle.backend.profile;

import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.profile.dto.MongleProfileResponse;
import com.mongle.backend.profile.dto.UpdateMongleProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PreferenceService와 동일한 동시생성-안전 패턴(그 클래스 주석 참고). */
@Service
@RequiredArgsConstructor
public class MongleProfileService {

    private final MongleProfileRepository mongleProfileRepository;
    private final MongleProfileRegistrar mongleProfileRegistrar;

    @Transactional
    public MongleProfileResponse get(String userId) {
        return MongleProfileResponse.from(findOrCreateDefault(userId));
    }

    /**
     * 로그인 응답용 조회 전용 메서드. 위의 get()은 row가 없으면 기본 row를 새로 만들기 때문에 로그인에서 쓰면 안 된다 —
     * 로그인만 했는데 빈 프로필 row가 생기면 안 된다. row가 없으면 그냥 미완료(false)다.
     * 완료 규칙 자체는 MongleProfile.isOnboardingCompleted() 한 곳에만 두고 여기서는 위임만 한다.
     */
    @Transactional(readOnly = true)
    public boolean isOnboardingCompleted(String userId) {
        return mongleProfileRepository.findById(userId)
                .map(MongleProfile::isOnboardingCompleted)
                .orElse(false);
    }

    @Transactional
    public MongleProfileResponse update(String userId, UpdateMongleProfileRequest request) {
        if (!mongleProfileRepository.existsById(userId)) {
            mongleProfileRegistrar.createDefaultIfAbsent(userId);
        }
        MongleProfile profile = findForUpdate(userId);
        profile.applyPatch(request.similarityTrait(), request.comfortStyle(), request.accessory());
        return MongleProfileResponse.from(profile);
    }

    private MongleProfile findOrCreateDefault(String userId) {
        return mongleProfileRepository.findById(userId)
                .orElseGet(() -> {
                    mongleProfileRegistrar.createDefaultIfAbsent(userId);
                    return findForUpdate(userId);
                });
    }

    private MongleProfile findForUpdate(String userId) {
        return mongleProfileRepository.findForUpdate(userId)
                .orElseThrow(() -> ApiException.conflict("프로필 생성 중 충돌이 발생했습니다. 다시 시도해주세요"));
    }
}
