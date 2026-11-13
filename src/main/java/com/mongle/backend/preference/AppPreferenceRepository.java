package com.mongle.backend.preference;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppPreferenceRepository extends JpaRepository<AppPreference, String> {

    /**
     * SELECT ... FOR UPDATE. REPEATABLE READ 스냅샷이 아니라 최신 커밋 버전을 읽어야 할 때
     * (방금 다른 트랜잭션이 만든 row) 쓴다 — PreferenceService.findOrCreateDefault 참고.
     * FOR SHARE가 아니라 FOR UPDATE인 이유: 첫 PATCH가 동시에 두 번 오면 둘 다 공유락을 쥔 채
     * 쓰기락으로 올리려다 데드락이 나므로, 처음부터 배타락으로 줄 세운다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AppPreference p where p.userId = :userId")
    Optional<AppPreference> findForUpdate(@Param("userId") String userId);
}
