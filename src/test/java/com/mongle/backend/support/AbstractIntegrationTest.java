package com.mongle.backend.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 모든 통합테스트가 상속받는 베이스. H2가 아니라 Testcontainers로 띄운 진짜 MySQL 8.4 위에서
 * 돈다 — 운영 스키마(CHAR/VARCHAR, ENUM 흉내 등)와 다르게 동작해서 한 번 사고 난 적이 있어서
 * (README "명세서 대비 의도적으로 보강/변경한 지점" 참고) 아예 같은 DB로 통일했다.
 *
 * 컨테이너는 static이라 이 클래스를 상속하는 모든 테스트 클래스가 JVM 안에서 하나를 공유한다
 * — Flyway 마이그레이션도 전체 테스트 스위트에서 딱 한 번만 돈다. 로컬·CI(GitHub Actions
 * ubuntu-latest) 둘 다 Docker가 있어야 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("mongle")
            .withUsername("mongle")
            .withPassword("mongle-test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        // serverTimezone=UTC는 운영 datasource URL(application.yml)에도 있는 설정인데 여기선
        // 빠져 있었다. 둘 다 JPA/Hibernate로만 쓰고 읽을 땐 내부적으로 자기 자신과만 일관되면
        // 돼서 드러나지 않았지만, 배치 스케줄러 테스트에서 raw JDBC로 과거 시각을 직접 심어보니
        // JPA가 읽는 값과 9시간(KST 오프셋)이 벌어지는 게 드러났다(2026-11-11). 운영 설정과 맞춰
        // 테스트 환경의 타임존 처리를 명확히 고정한다.
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?serverTimezone=UTC&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
