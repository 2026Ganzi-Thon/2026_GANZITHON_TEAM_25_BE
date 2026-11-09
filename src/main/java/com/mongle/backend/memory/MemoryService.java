package com.mongle.backend.memory;

import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.memory.dto.ApproveMemoryResponse;
import com.mongle.backend.memory.dto.MemoryListResponse;
import com.mongle.backend.memory.dto.MemoryResponse;
import com.mongle.backend.memory.dto.PreviewMemoryResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final String LIMIT_WARNING = "memory_limit_near";

    private final ApprovedMemoryRepository approvedMemoryRepository;
    private final MemoryPreviewRepository memoryPreviewRepository;
    private final MemorySummarizer memorySummarizer;

    @Value("${mongle.memory.soft-limit}")
    private int softLimit;

    @Value("${mongle.memory.preview-ttl-minutes}")
    private int previewTtlMinutes;

    @Transactional(readOnly = true)
    public MemoryListResponse list(String userId) {
        List<MemoryResponse> memories = approvedMemoryRepository.findByUserIdOrderByApprovedAtDesc(userId).stream()
                .map(MemoryResponse::from)
                .toList();
        return new MemoryListResponse(memories);
    }

    @Transactional
    public MemoryResponse update(String userId, String memoryId, String summary) {
        ApprovedMemory memory = findOwned(userId, memoryId);
        memory.editSummary(summary);
        return MemoryResponse.from(memory);
    }

    @Transactional
    public void delete(String userId, String memoryId) {
        ApprovedMemory memory = findOwned(userId, memoryId);
        approvedMemoryRepository.delete(memory); // 하드 삭제 — soft delete 금지 (명세서 4.2)
    }

    @Transactional
    public void deleteAll(String userId) {
        approvedMemoryRepository.deleteByUserId(userId);
    }

    @Transactional
    public PreviewMemoryResponse preview(String userId, String sessionId) {
        String draft = memorySummarizer.summarize(sessionId);
        MemoryPreview saved = memoryPreviewRepository.save(MemoryPreview.create(userId, sessionId, draft));
        return new PreviewMemoryResponse(saved.getPreviewId(), saved.getDraftSummary());
    }

    @Transactional
    public ApproveMemoryResponse approve(String userId, String previewId, String finalSummary) {
        MemoryPreview preview = memoryPreviewRepository.findById(previewId)
                .filter(p -> p.isOwnedBy(userId))
                .filter(p -> !p.isExpired(Instant.now(), previewTtlMinutes))
                .orElseThrow(() -> ApiException.notFound("미리보기를 찾을 수 없거나 만료됐습니다"));

        ApprovedMemory memory = approvedMemoryRepository.save(ApprovedMemory.approve(userId, finalSummary));
        memoryPreviewRepository.delete(preview); // 승인 즉시 원본 재사용 방지

        String warning = approvedMemoryRepository.countByUserId(userId) > softLimit ? LIMIT_WARNING : null;
        return new ApproveMemoryResponse(memory.getMemoryId(), memory.getApprovedSummary(), memory.getApprovedAt(), warning);
    }

    private ApprovedMemory findOwned(String userId, String memoryId) {
        ApprovedMemory memory = approvedMemoryRepository.findById(memoryId)
                .orElseThrow(() -> ApiException.notFound("기억을 찾을 수 없습니다"));
        if (!memory.isOwnedBy(userId)) {
            throw ApiException.forbidden("본인 기억이 아닙니다");
        }
        return memory;
    }
}
