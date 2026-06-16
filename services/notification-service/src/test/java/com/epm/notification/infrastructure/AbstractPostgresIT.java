package com.epm.notification.infrastructure;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that require a real PostgreSQL database.
 *
 * <p>Uses a JVM-wide singleton {@link PostgreSQLContainer}: the container is started once
 * on first class-load via the {@code static} initializer and is never stopped explicitly —
 * Testcontainers' Ryuk resource-reaper reclaims it at JVM exit. This prevents the
 * per-class container lifecycle ({@code @Testcontainers} + {@code @Container}) from
 * closing the container while a cached Spring context still holds open Hikari connections,
 * which previously caused {@code CannotCreateTransactionException} / 30-second pool
 * timeouts when multiple IT classes shared the same Spring application context.
 *
 * <p>{@code @ServiceConnection} on the {@code static} field is supported by
 * Spring Boot 3.1+ and remains the mechanism by which Spring Boot auto-configures
 * the {@code DataSource} and Flyway — no {@code @DynamicPropertySource} boilerplate needed.
 */
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractPostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }
}
