package com.mongle.backend.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByDeviceId(String deviceId);

    /**
     * SELECT ... FOR UPDATE. REPEATABLE READ 스냅샷이 아니라 최신 커밋 버전을 읽어야 할 때
     * (방금 다른 트랜잭션이 가입시킨 row) 쓴다 — AuthService.findOrCreate 참고.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.deviceId = :deviceId")
    Optional<AppUser> findByDeviceIdForUpdate(@Param("deviceId") String deviceId);
}
