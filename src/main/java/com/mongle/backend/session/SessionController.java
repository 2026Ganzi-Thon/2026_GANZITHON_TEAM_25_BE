package com.mongle.backend.session;

import com.mongle.backend.common.security.CurrentUserId;
import com.mongle.backend.session.dto.FinishSessionResponse;
import com.mongle.backend.session.dto.StartSessionRequest;
import com.mongle.backend.session.dto.StartSessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API_명세서_v1 4.1 */
@RestController
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/sessions")
    public ResponseEntity<StartSessionResponse> start(
            @CurrentUserId String userId,
            @Valid @RequestBody StartSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.start(userId, request));
    }

    @PostMapping("/sessions/{sessionId}/finish")
    public ResponseEntity<FinishSessionResponse> finish(
            @CurrentUserId String userId,
            @PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.finish(userId, sessionId));
    }
}
