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
