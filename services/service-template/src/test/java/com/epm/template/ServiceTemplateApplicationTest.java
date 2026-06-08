package com.epm.template;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: verifies the Spring context loads without errors.
 *
 * <p>This is the minimum viable integration test. If wiring is broken
 * (missing beans, circular dependencies, misconfigured properties),
 * this test catches it before anything reaches production.
 *
 * <p>{@code spring.config.import} is overridden to prevent Spring Cloud Config Client
 * from requiring a running Config Server during tests.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.config.import=optional:configserver:",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ServiceTemplateApplicationTest {

    @Test
    void contextLoads() {
        // If the context fails to start, this test fails with a clear error.
    }
}
