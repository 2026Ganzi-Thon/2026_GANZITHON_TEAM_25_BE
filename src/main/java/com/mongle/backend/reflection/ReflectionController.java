package com.mongle.backend.reflection;

import com.mongle.backend.common.security.CurrentUserId;
import com.mongle.backend.reflection.dto.ReflectionRequest;
import com.mongle.backend.reflection.dto.ReflectionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API_명세서_v1 4.3 — AI 연동 담당(백엔드 B + 프론트)의 핵심 엔드포인트. */
@RestController
@RequiredArgsConstructor
public class ReflectionController {

    private final ReflectionService reflectionService;

    @PostMapping("/sessions/{sessionId}/reflections")
    public ResponseEntity<ReflectionResponse> reflect(
            @CurrentUserId String userId,
            @PathVariable String sessionId,
            @Valid @RequestBody ReflectionRequest request) {
        return ResponseEntity.ok(reflectionService.reflect(userId, sessionId, request));
    }
}
