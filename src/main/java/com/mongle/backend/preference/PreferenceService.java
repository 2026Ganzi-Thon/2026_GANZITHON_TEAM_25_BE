package com.mongle.backend.preference;

import com.mongle.backend.preference.dto.PreferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final AppPreferenceRepository appPreferenceRepository;

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

    private AppPreference findOrCreateDefault(String userId) {
        return appPreferenceRepository.findById(userId)
                .orElseGet(() -> appPreferenceRepository.save(AppPreference.defaultFor(userId)));
    }
}
