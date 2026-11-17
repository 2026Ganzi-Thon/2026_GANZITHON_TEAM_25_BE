package com.mongle.backend.profile;

import com.mongle.backend.common.security.CurrentUserId;
import com.mongle.backend.profile.dto.MongleProfileResponse;
import com.mongle.backend.profile.dto.UpdateMongleProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 2026-11-18 — 온보딩(나를 닮은 특징·편안한 성향·장식) 저장. PreferenceController와 같은 패턴. */
@RestController
@RequiredArgsConstructor
public class MongleProfileController {

    private final MongleProfileService mongleProfileService;

    @GetMapping("/mongle-profile")
    public ResponseEntity<MongleProfileResponse> get(@CurrentUserId String userId) {
        return ResponseEntity.ok(mongleProfileService.get(userId));
    }

    @PatchMapping("/mongle-profile")
    public ResponseEntity<MongleProfileResponse> update(
            @CurrentUserId String userId,
            @Valid @RequestBody UpdateMongleProfileRequest request) {
        return ResponseEntity.ok(mongleProfileService.update(userId, request));
    }
}
