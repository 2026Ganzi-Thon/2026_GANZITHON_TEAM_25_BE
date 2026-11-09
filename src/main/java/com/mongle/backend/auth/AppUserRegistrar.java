package com.mongle.backend.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * device_id 동시 가입(race condition) 처리를 AuthService의 트랜잭션과 분리해두는 전용 빈.
 *
 * 왜 별도 빈인가: 같은 클래스 안에서 @Transactional(REQUIRES_NEW) 메서드를 self-invocation으로
 * 부르면 Spring AOP 프록시를 안 거쳐서 propagation이 적용되지 않는다. 그리고 REQUIRES_NEW가 꼭
 * 필요한 이유는, UNIQUE 제약 위반은 보통 flush 시점(=커밋 시점)에야 터지는데 그걸 바깥 트랜잭션
 * 안에서 잡으면 Spring이 이미 그 트랜잭션을 rollback-only로 표시해버려서, 같은 트랜잭션에서
 * "진 쪽이 다시 조회"하는 복구 자체가 실패한다. 그래서 가입 시도를 별도 트랜잭션으로 떼어내고,
 * 실패하면 그 트랜잭션만 롤백시킨 뒤 바깥에서 다시 조회한다.
 */
@Component
public class AppUserRegistrar {

    private final AppUserRepository appUserRepository;

    public AppUserRegistrar(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AppUser registerOrNull(String deviceId) {
        try {
            return appUserRepository.saveAndFlush(AppUser.register(deviceId));
        } catch (DataIntegrityViolationException raceLost) {
            return null; // 호출자가 findByDeviceId로 승자의 row를 다시 읽는다
        }
    }
}
