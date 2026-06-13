package com.epm.template.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import com.epm.template.domain.model.Example;
import com.epm.template.domain.port.out.ExampleEventPublisher;
import com.epm.template.domain.port.out.TransactionalExampleWriter;
import com.epm.template.infrastructure.adapter.out.persistence.ExampleJpaRepository;
import com.epm.template.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test proving outbox atomicity: if the {@link ExampleEventPublisher} throws
 * after a successful aggregate save, BOTH the {@code examples} row and the {@code outbox_events}
 * row must be absent after the transaction rolls back.
 *
 * <p>Uses {@code @SpringBootTest} (NON-transactional by default) with a real
 * Testcontainers PostgreSQL database so that the {@code @Transactional} boundary on
 * {@link TransactionalExampleWriter#saveAndPublish} creates and rolls back a real
 * database transaction — not just an in-test mock.
 *
 * <p>The {@link ExampleEventPublisher} is replaced with a Mockito mock that throws on
 * first call, simulating an outbox write failure after the aggregate save.
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
class OutboxAtomicityIT extends AbstractPostgresIT {

    @Autowired
    private TransactionalExampleWriter writer;

    @Autowired
    private ExampleJpaRepository exampleJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @MockitoBean
    private ExampleEventPublisher eventPublisher;

    @AfterEach
    void cleanup() {
        outboxEventJpaRepository.deleteAll();
        exampleJpaRepository.deleteAll();
    }

    /**
     * Proves that when the ExampleEventPublisher throws after a successful aggregate save,
     * the entire transaction rolls back: no examples row and no outbox_events row remain in the DB.
     *
     * <p>This is the real rollback proof — we read state from a FRESH read after the
     * failed saveAndPublish call completes (the test method itself is NOT @Transactional).
     */
    @Test
    void saveAndPublish_rollsBackBothExampleAndOutboxOnPublisherFailure() {
        UUID tenantId = UUID.randomUUID();
        Example example = Example.create(tenantId, "Atomic Test Example");

        // Simulate publisher failure (outbox insert fails) after the aggregate save
        doThrow(new RuntimeException("Simulated publisher failure for atomicity test"))
                .when(eventPublisher).publish(any());

        // saveAndPublish must propagate the publisher exception
        assertThatThrownBy(() -> writer.saveAndPublish(example))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated publisher failure");

        // Fresh read (new transaction) — both rows must be absent after rollback
        assertThat(exampleJpaRepository.findAll())
                .as("Example row must not exist after transaction rollback")
                .isEmpty();
        assertThat(outboxEventJpaRepository.findAll())
                .as("Outbox row must not exist after transaction rollback")
                .isEmpty();
    }
}
