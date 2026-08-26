package com.pos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Disposable PostgreSQL instance for integration tests.
 *
 * <p>System Architecture Document section 29 requires repository and integration tests to run
 * against PostgreSQL-compatible test infrastructure. The image is pinned to the same version the
 * application runs against in docker-compose.yml, so migrations and constraints are exercised
 * exactly as they behave at runtime.
 *
 * <p>{@code @ServiceConnection} publishes the container's JDBC coordinates to the Spring context,
 * which means the application datasource and Flyway share one connection and one container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:15-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE);
    }

    @Bean
    @ServiceConnection(name = "redis")
    org.testcontainers.containers.GenericContainer<?> redisContainer() {
        return new org.testcontainers.containers.GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }
}
