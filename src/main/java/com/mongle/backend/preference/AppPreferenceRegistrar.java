package com.mongle.backend.preference;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 첫 GET /preferences 동시 호출(앱 기동 직후 여러 화면이 동시에 조회 등) 시 기본값 row 생성을
 * PreferenceService의 트랜잭션과 분리해두는 전용 빈. auth 모듈의 AppUserRegistrar와 같은 발상이다.
 *
 * 왜 별도 트랜잭션(REQUIRES_NEW)인가: user_id PK 중복은 flush 시점에야 터지는데, 그걸 바깥
 * 트랜잭션 안에서 잡으면 Spring이 이미 그 트랜잭션을 rollback-only로 표시해버려서 같은
 * 트랜잭션에서 "진 쪽이 다시 조회"하는 복구 자체가 실패한다. 그래서 생성 시도만 새 트랜잭션으로 뗀다.
 *
 * 왜 @Transactional(REQUIRES_NEW) 메서드 안에서 catch하지 않고 TransactionTemplate 바깥에서
 * catch하나: saveAndFlush 자체가 @Transactional이라, 예외가 그 프록시를 빠져나오는 순간 REQUIRES_NEW
 * 트랜잭션도 rollback-only가 된다. 메서드 안에서 예외를 삼키면 커밋 시점에
 * UnexpectedRollbackException이 터진다(동시성 테스트로 실제 확인, 2026-11-13). 그래서 새 트랜잭션이
 * 완전히 롤백되고 끝난 "뒤"에 예외를 잡는다.
 */
@Component
public class AppPreferenceRegistrar {

    private final AppPreferenceRepository appPreferenceRepository;
    private final TransactionTemplate requiresNew;

    public AppPreferenceRegistrar(AppPreferenceRepository appPreferenceRepository,
                                  PlatformTransactionManager transactionManager) {
        this.appPreferenceRepository = appPreferenceRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return 이번 호출이 row를 만들었으면 true, 다른 요청이 먼저 만들어서 졌으면 false */
    public boolean createDefaultIfAbsent(String userId) {
        try {
            requiresNew.executeWithoutResult(status ->
                    appPreferenceRepository.saveAndFlush(AppPreference.defaultFor(userId)));
            return true;
        } catch (DataIntegrityViolationException raceLost) {
            return false; // 호출자가 승자의 row를 다시 읽는다
        }
    }
}
