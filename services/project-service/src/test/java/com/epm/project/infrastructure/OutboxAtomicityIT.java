package com.epm.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.model.Project;
import com.epm.project.domain.port.out.DomainEventPublisher;
import com.epm.project.domain.port.out.TransactionalOutboxWriter;
import com.epm.project.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.epm.project.infrastructure.adapter.out.persistence.ProjectJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

/**
 * Integration test proving outbox atomicity: if the {@link DomainEventPublisher} throws
 * after a successful aggregate save, BOTH the {@code projects} row and the
 * {@code outbox_events} row must be absent after the transaction rolls back.
 *
 * <p>Uses {@code @SpringBootTest} (NOT {@code @Transactional}) with a real Testcontainers
 * PostgreSQL database so that the {@code @Transactional} boundary on
 * {@link TransactionalOutboxWriter#saveAndPublish} creates and rolls back a real
 * database transaction — not just an in-test mock.
 *
 * <p>{@link DomainEventPublisher} is replaced with a Mockito mock that throws on
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
    private TransactionalOutboxWriter outboxWriter;

    @Autowired
    private ProjectJpaRepository projectJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @MockitoBean
    private DomainEventPublisher eventPublisher;

    @AfterEach
    void cleanup() {
        outboxEventJpaRepository.deleteAll();
        projectJpaRepository.deleteAll();
    }

    /**
     * Proves that when the DomainEventPublisher throws after a successful aggregate save,
     * the entire transaction rolls back: no projects row and no outbox_events row remain.
     *
     * <p>The test itself is NOT {@code @Transactional} so that the {@code saveAndPublish}
     * call runs in its own committed-or-rolled-back transaction that is visible to the
     * subsequent fresh reads.
     */
    @Test
    void saveAndPublish_rollsBackBothProjectAndOutboxOnPublisherFailure() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Atomic Test Project", "desc", ProjectVisibility.PRIVATE, ownerId, tenantId));

        // Simulate publisher failure (outbox insert fails) after the aggregate save
        doThrow(new RuntimeException("Simulated publisher failure for atomicity test"))
                .when(eventPublisher).publish(any());

        // saveAndPublish must propagate the publisher exception
        assertThatThrownBy(() -> outboxWriter.saveAndPublish(project))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated publisher failure");

        // Fresh read (new transaction) — both rows must be absent after rollback
        assertThat(projectJpaRepository.findAll())
                .as("Project row must not exist after transaction rollback")
                .isEmpty();
        assertThat(outboxEventJpaRepository.findAll())
                .as("Outbox row must not exist after transaction rollback")
                .isEmpty();
    }
}
