package com.mongle.backend.memory;

import com.mongle.backend.common.security.CurrentUserId;
import com.mongle.backend.memory.dto.ApproveMemoryRequest;
import com.mongle.backend.memory.dto.ApproveMemoryResponse;
import com.mongle.backend.memory.dto.MemoryListResponse;
import com.mongle.backend.memory.dto.MemoryResponse;
import com.mongle.backend.memory.dto.PreviewMemoryRequest;
import com.mongle.backend.memory.dto.PreviewMemoryResponse;
import com.mongle.backend.memory.dto.UpdateMemoryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API_명세서_v1 4.2, 4.4 */
@RestController
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    @GetMapping("/memories")
    public ResponseEntity<MemoryListResponse> list(@CurrentUserId String userId) {
        return ResponseEntity.ok(memoryService.list(userId));
    }

    @PatchMapping("/memories/{memoryId}")
    public ResponseEntity<MemoryResponse> update(
            @CurrentUserId String userId,
            @PathVariable String memoryId,
            @Valid @RequestBody UpdateMemoryRequest request) {
        return ResponseEntity.ok(memoryService.update(userId, memoryId, request.summary()));
    }

    @DeleteMapping("/memories/{memoryId}")
    public ResponseEntity<Void> delete(@CurrentUserId String userId, @PathVariable String memoryId) {
        memoryService.delete(userId, memoryId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/memories")
    public ResponseEntity<Void> deleteAll(@CurrentUserId String userId) {
        memoryService.deleteAll(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/memories/preview")
    public ResponseEntity<PreviewMemoryResponse> preview(
            @CurrentUserId String userId,
            @Valid @RequestBody PreviewMemoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memoryService.preview(userId, request.sessionId()));
    }

    @PostMapping("/memories")
    public ResponseEntity<ApproveMemoryResponse> approve(
            @CurrentUserId String userId,
            @Valid @RequestBody ApproveMemoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memoryService.approve(userId, request.previewId(), request.finalSummary()));
    }
}
