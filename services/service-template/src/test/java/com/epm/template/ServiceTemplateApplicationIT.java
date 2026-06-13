package com.epm.template;

import com.epm.template.infrastructure.AbstractPostgresIT;
import com.epm.template.infrastructure.adapter.out.messaging.KafkaOutboxPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke test: verifies the Spring context loads without errors against a real
 * Testcontainers PostgreSQL database (so it is named {@code *IT} and runs under Failsafe).
 *
 * <p>This is the minimum viable integration test. If wiring is broken
 * (missing beans, circular dependencies, misconfigured properties),
 * this test catches it before anything reaches production.
 *
 * <p>{@code spring.config.import} is overridden to prevent Spring Cloud Config Client
 * from requiring a running Config Server during tests.
 *
 * <p>{@link KafkaOutboxPublisher} is mocked so no real Kafka broker is needed for a
 * context smoke test.
 *
 * <p>{@code spring.task.scheduling.enabled=false} disables the {@code @Scheduled} relay
 * during this smoke test only — not to dodge SQL errors (the DB is real Postgres and the
 * SKIP LOCKED queries run fine), but to keep the context-load check free of background
 * relay noise. The relay SQL itself is exercised by {@code OutboxRelayIT}.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.task.scheduling.enabled=false"
})
class ServiceTemplateApplicationIT extends AbstractPostgresIT {

    @MockitoBean
    private KafkaOutboxPublisher kafkaOutboxPublisher;

    @Test
    void contextLoads() {
        // If the context fails to start, this test fails with a clear error.
    }
}
