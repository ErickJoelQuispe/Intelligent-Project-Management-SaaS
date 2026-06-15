package com.epm.task.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;
import com.epm.task.infrastructure.adapter.out.persistence.ActivityLogJpaRepository;
import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.epm.task.infrastructure.adapter.out.persistence.TaskJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test proving outbox atomicity: if the {@link DomainEventPublisher} throws
 * after a successful aggregate save, BOTH the {@code tasks} row and the
 * {@code outbox_events} row must be absent after the transaction rolls back.
 *
 * <p>Uses {@code @SpringBootTest} (NOT {@code @Transactional}) with a real Testcontainers
 * PostgreSQL database so that the {@code @Transactional} boundary on
 * {@link TransactionalOutboxWriter#saveAndPublish} creates and rolls back a real
 * database transaction — not just an in-test mock.
 *
 * <p>{@link DomainEventPublisher} is replaced with a Mockito mock that throws on
 * first call, simulating an outbox write failure after the aggregate save.
 *
 * <p>Requires Docker (Testcontainers). If Docker is unavailable the test will fail to
 * start — do NOT weaken it to make it pass.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.task.scheduling.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json"
})
class OutboxAtomicityIT extends AbstractPostgresIT {

    @Autowired
    private TransactionalOutboxWriter outboxWriter;

    @Autowired
    private TaskJpaRepository taskJpaRepository;

    @Autowired
    private ActivityLogJpaRepository activityLogJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @MockitoBean
    private DomainEventPublisher eventPublisher;

    @AfterEach
    void cleanup() {
        // Delete in FK-safe order: activity_log references tasks
        outboxEventJpaRepository.deleteAll();
        activityLogJpaRepository.deleteAll();
        taskJpaRepository.deleteAll();
    }

    /**
     * Proves that when the DomainEventPublisher throws after a successful aggregate save,
     * the entire transaction rolls back: no tasks row and no outbox_events row remain.
     *
     * <p>The test itself is NOT {@code @Transactional} so that the {@code saveAndPublish}
     * call runs in its own committed-or-rolled-back transaction that is visible to the
     * subsequent fresh reads.
     */
    @Test
    void saveAndPublish_rollsBackBothTaskAndOutboxOnPublisherFailure() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Task task = Task.create(new CreateTaskCommand(
                tenantId, projectId, UUID.randomUUID(),
                "Atomic Test Task", "desc", TaskPriority.MEDIUM, null, null));

        // Simulate publisher failure (outbox insert fails) after the aggregate save
        doThrow(new RuntimeException("Simulated publisher failure for atomicity test"))
                .when(eventPublisher).publish(any());

        // saveAndPublish must propagate the publisher exception
        assertThatThrownBy(() -> outboxWriter.saveAndPublish(task, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated publisher failure");

        // Fresh read (new transaction) — both rows must be absent after rollback
        assertThat(taskJpaRepository.findAll())
                .as("Task row must not exist after transaction rollback")
                .isEmpty();
        assertThat(outboxEventJpaRepository.findAll())
                .as("Outbox row must not exist after transaction rollback")
                .isEmpty();
    }
}
