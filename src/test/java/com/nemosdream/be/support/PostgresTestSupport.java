package com.nemosdream.be.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 실제 PostgreSQL(운영과 같은 16) 컨테이너로 테스트한다. 배열 컬럼 등 H2 로는 확인할 수 없는 부분 때문.
 * Docker 가 없으면 테스트를 건너뛴다 (CI 의 GitHub Runner 에는 Docker 가 있어 항상 실행된다).
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresTestSupport {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");
}
