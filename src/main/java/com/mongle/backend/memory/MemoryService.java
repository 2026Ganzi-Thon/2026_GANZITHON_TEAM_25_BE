package com.mongle.backend.memory;

import com.mongle.backend.common.error.ApiException;
import com.mongle.backend.memory.dto.ApproveMemoryResponse;
import com.mongle.backend.memory.dto.MemoryListResponse;
import com.mongle.backend.memory.dto.MemoryResponse;
import com.mongle.backend.memory.dto.PreviewMemoryResponse;
import com.mongle.backend.session.SessionService;
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
    private static final String DEFAULT_DRAFT = "오늘의 대화";
    // draft_summary VARCHAR(300). 사용자가 승인·수정하는 요약은 @Size(max=300)로 막지만 Gemini 초안은
    // 검증 경로가 없어서, 모델이 길게 답하면 DB 오류로 500이 났다(2026-11-13 재현 테스트로 확인).
    private static final int MAX_DRAFT_LENGTH = 300;

    private final ApprovedMemoryRepository approvedMemoryRepository;
    private final MemoryPreviewRepository memoryPreviewRepository;
    private final MemorySummarizer memorySummarizer;
    private final SessionService sessionService;

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
        sessionService.verifyOwned(userId, sessionId); // 없으면 404, 남의 세션이면 403 — 타인 대화 요약 유출 방지
        String draft = normalizeDraft(memorySummarizer.summarize(sessionId));
        MemoryPreview saved = memoryPreviewRepository.save(MemoryPreview.create(userId, sessionId, draft));
        return new PreviewMemoryResponse(saved.getPreviewId(), saved.getDraftSummary());
    }

    /** 모델 출력은 믿지 않는다: 비었으면 기본 문구, 300자를 넘으면 말줄임표로 잘라 컬럼에 맞춘다. */
    private static String normalizeDraft(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            return DEFAULT_DRAFT;
        }
        if (text.length() <= MAX_DRAFT_LENGTH) {
            return text;
        }
        int end = MAX_DRAFT_LENGTH - 1; // 말줄임표 한 글자 자리
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--; // 이모지 같은 서로게이트 쌍을 반으로 자르지 않는다
        }
        return text.substring(0, end) + "…";
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
