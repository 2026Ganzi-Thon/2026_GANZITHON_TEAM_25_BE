package com.mongle.backend.memory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovedMemoryRepository extends JpaRepository<ApprovedMemory, String> {

    List<ApprovedMemory> findByUserIdOrderByApprovedAtDesc(String userId);

    List<ApprovedMemory> findByUserIdAndMemoryIdIn(String userId, List<String> memoryIds);

    long countByUserId(String userId);

    void deleteByUserId(String userId);
}
