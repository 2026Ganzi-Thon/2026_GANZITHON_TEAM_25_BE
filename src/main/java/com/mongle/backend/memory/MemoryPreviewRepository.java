package com.mongle.backend.memory;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryPreviewRepository extends JpaRepository<MemoryPreview, String> {

    @Modifying
    @Query("DELETE FROM MemoryPreview p WHERE p.createdAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
