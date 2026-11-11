package com.mongle.backend.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongle.backend.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** API_명세서_v1 4.4 — 지금까지 테스트가 없던 배치 스케줄러. 직접 @Scheduled 메서드를 호출해 검증한다. */
class MemoryPreviewCleanupSchedulerTest extends AbstractIntegrationTest {

    @Autowired private MemoryPreviewRepository memoryPreviewRepository;
    @Autowired private MemoryPreviewCleanupScheduler scheduler;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void TTL이_지난_미리보기는_삭제된다() {
        MemoryPreview preview = memoryPreviewRepository.save(
                MemoryPreview.create("user-ttl-expired", UUID.randomUUID().toString(), "오늘의 대화"));
        backdateCreatedAt(preview.getPreviewId(), Instant.now().minusSeconds(31 * 60L));

        scheduler.purgeExpiredPreviews();

        assertThat(memoryPreviewRepository.findById(preview.getPreviewId())).isEmpty();
    }

    @Test
    void TTL_이내의_미리보기는_삭제되지_않는다() {
        MemoryPreview preview = memoryPreviewRepository.save(
                MemoryPreview.create("user-ttl-fresh", UUID.randomUUID().toString(), "오늘의 대화"));
        backdateCreatedAt(preview.getPreviewId(), Instant.now().minusSeconds(5 * 60L));

        scheduler.purgeExpiredPreviews();

        assertThat(memoryPreviewRepository.findById(preview.getPreviewId())).isPresent();
    }

    private void backdateCreatedAt(String previewId, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE memory_preview SET created_at = ? WHERE preview_id = ?",
                Timestamp.from(createdAt), previewId);
    }
}
