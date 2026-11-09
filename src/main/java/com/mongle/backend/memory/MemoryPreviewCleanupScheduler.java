package com.mongle.backend.memory;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** API_명세서_v1 4.4 — 미리보기만 보고 승인 안 한 건 30분 뒤 배치로 하드 삭제한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryPreviewCleanupScheduler {

    private final MemoryPreviewRepository memoryPreviewRepository;

    @Value("${mongle.memory.preview-ttl-minutes}")
    private int ttlMinutes;

    @Scheduled(fixedDelay = 10 * 60 * 1000) // 10분마다
    @Transactional
    public void purgeExpiredPreviews() {
        Instant cutoff = Instant.now().minusSeconds(ttlMinutes * 60L);
        int deleted = memoryPreviewRepository.deleteExpiredBefore(cutoff);
        if (deleted > 0) {
            log.info("만료된 기억 미리보기 {}건 삭제", deleted);
        }
    }
}
