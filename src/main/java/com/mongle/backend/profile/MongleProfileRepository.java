package com.mongle.backend.profile;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MongleProfileRepository extends JpaRepository<MongleProfile, String> {

    /** SELECT ... FOR UPDATE — AppPreferenceRepository.findForUpdate와 같은 이유로 필요하다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MongleProfile p where p.userId = :userId")
    Optional<MongleProfile> findForUpdate(@Param("userId") String userId);
}
