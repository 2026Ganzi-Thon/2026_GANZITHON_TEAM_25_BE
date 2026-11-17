package com.mongle.backend.profile;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AppPreferenceRegistrar와 똑같은 이유로 생성 시도만 별도 트랜잭션(REQUIRES_NEW)으로 뗀다 —
 * 그 클래스 주석에 레이스·롤백 관련 상세 설명이 있다.
 */
@Component
public class MongleProfileRegistrar {

    private final MongleProfileRepository mongleProfileRepository;
    private final TransactionTemplate requiresNew;

    public MongleProfileRegistrar(MongleProfileRepository mongleProfileRepository,
                                  PlatformTransactionManager transactionManager) {
        this.mongleProfileRepository = mongleProfileRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return 이번 호출이 row를 만들었으면 true, 다른 요청이 먼저 만들어서 졌으면 false */
    public boolean createDefaultIfAbsent(String userId) {
        try {
            requiresNew.executeWithoutResult(status ->
                    mongleProfileRepository.saveAndFlush(MongleProfile.defaultFor(userId)));
            return true;
        } catch (DataIntegrityViolationException raceLost) {
            return false; // 호출자가 승자의 row를 다시 읽는다
        }
    }
}
