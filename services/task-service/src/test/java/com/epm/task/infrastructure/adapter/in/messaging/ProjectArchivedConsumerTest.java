package com.epm.task.infrastructure.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.task.domain.model.TaskStatus;
import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.epm.task.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.epm.task.infrastructure.adapter.out.persistence.TaskJpaEntity;
import com.epm.task.infrastructure.adapter.out.persistence.TaskJpaRepository;
import com.epm.task.domain.model.TaskPriority;
import org.apache.kafka.clients.producer.ProducerRecord;
import com.epm.task.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for {@link ProjectArchivedConsumer}.
 *
 * <p>Uses @EmbeddedKafka to publish real Kafka messages and verify task cancellation.
 * DB is provided by Testcontainers via AbstractPostgresIT (@ServiceConnection).
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"project.project.archived"})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json"
})
class ProjectArchivedConsumerTest extends AbstractPostgresIT {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TaskJpaRepository taskJpaRepo;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepo;

    @Autowired
    private OutboxEventJpaRepository outboxRepo;

    private UUID tenantId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        // Seed 3 tasks with various statuses
        taskJpaRepo.save(buildTask(tenantId, projectId, TaskStatus.TODO));
        taskJpaRepo.save(buildTask(tenantId, projectId, TaskStatus.IN_PROGRESS));
        taskJpaRepo.save(buildTask(tenantId, projectId, TaskStatus.IN_REVIEW));
    }

    // ── Scenario: ProjectArchived cancels all tasks ─────────────────────────

    @Test
    void projectArchived_cancelsAllTasksInProject() throws Exception {
        String eventId = UUID.randomUUID().toString();
        kafkaTemplate.send(new ProducerRecord<>(
                "project.project.archived",
                projectId.toString(),
                buildProjectArchivedMessage(eventId, projectId, tenantId)));

        // Poll until all tasks are CANCELLED
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<TaskJpaEntity> tasks = taskJpaRepo.findAllByProjectIdAndTenantId(projectId, tenantId);
            boolean allCancelled = tasks.stream()
                    .allMatch(t -> t.getStatus() == TaskStatus.CANCELLED);
            if (allCancelled && !tasks.isEmpty()) {
                break;
            }
            Thread.sleep(200);
        }

        List<TaskJpaEntity> tasks = taskJpaRepo.findAllByProjectIdAndTenantId(projectId, tenantId);
        assertThat(tasks).isNotEmpty();
        assertThat(tasks).allSatisfy(t ->
                assertThat(t.getStatus()).isEqualTo(TaskStatus.CANCELLED));
        assertThat(processedEventRepo.existsByEventId(eventId)).isTrue();
    }

    // ── Scenario: Duplicate event is idempotent ───────────────────────────────

    @Test
    void projectArchived_idempotent_duplicateEventSkipped() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String message = buildProjectArchivedMessage(eventId, projectId, tenantId);

        // First event — cancels tasks
        kafkaTemplate.send(new ProducerRecord<>(
                "project.project.archived", projectId.toString(), message));
        Thread.sleep(4000);

        assertThat(processedEventRepo.existsByEventId(eventId)).isTrue();
        long outboxCountAfterFirst = outboxRepo.count();

        // Second event — must be skipped (no new outbox rows)
        kafkaTemplate.send(new ProducerRecord<>(
                "project.project.archived", projectId.toString(), message));
        Thread.sleep(2000);

        long outboxCountAfterSecond = outboxRepo.count();
        assertThat(outboxCountAfterSecond).isEqualTo(outboxCountAfterFirst);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TaskJpaEntity buildTask(UUID tenantId, UUID projectId, TaskStatus status) {
        TaskJpaEntity entity = new TaskJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setProjectId(projectId);
        entity.setTitle("Task for archive test");
        entity.setStatus(status);
        entity.setPriority(TaskPriority.MEDIUM);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private String buildProjectArchivedMessage(String eventId, UUID projectId, UUID tenantId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "ProjectArchived",
                  "tenantId": "%s",
                  "occurredAt": "%s",
                  "payload": {
                    "projectId": "%s"
                  }
                }
                """.formatted(eventId, tenantId, Instant.now(), projectId);
    }
}
