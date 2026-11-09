package com.mongle.backend.preference;

import com.mongle.backend.common.security.CurrentUserId;
import com.mongle.backend.preference.dto.PreferenceResponse;
import com.mongle.backend.preference.dto.UpdatePreferenceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API_명세서_v1 6장 — 프론트가 "저장 안 되는데요?" 하기 딱 좋은 지점이라 명세서에서 따로 뺐던 것. */
@RestController
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping("/preferences")
    public ResponseEntity<PreferenceResponse> get(@CurrentUserId String userId) {
        return ResponseEntity.ok(preferenceService.get(userId));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<PreferenceResponse> update(
            @CurrentUserId String userId,
            @RequestBody UpdatePreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.update(
                userId, request.soundEnabled(), request.hapticsEnabled(), request.animationEnabled()));
    }
}
