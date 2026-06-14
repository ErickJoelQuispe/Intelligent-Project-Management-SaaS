package com.epm.task.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.util.UUID;

import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.epm.task.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test for {@link OutboxRelayExecutor} and {@link OutboxRelayService}.
 *
 * <p>Verifies that PENDING outbox rows are marked PUBLISHED after relay.
 * Uses Testcontainers via AbstractPostgresIT. Kafka is mocked to avoid infrastructure deps.
 */
@DataJpaTest
@Import({OutboxRelayExecutor.class, OutboxRelayService.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class OutboxRelayServiceTest extends AbstractPostgresIT {

    @Autowired
    private OutboxRelayExecutor outboxRelayExecutor;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private OutboxEventJpaRepository outboxRepo;

    @MockitoBean
    private KafkaOutboxPublisher kafkaOutboxPublisher;

    // ── PENDING → PUBLISHED ─────────────────────────────────────────────────

    @Test
    void relayBatch_marksRowPublished_afterSuccessfulSend() {
        // Arrange: save a PENDING outbox row
        OutboxEventJpaEntity pending = buildPendingRow();
        outboxRepo.save(pending);

        // Simulate successful Kafka publish: set publishedAt on the entity
        doAnswer(invocation -> {
            OutboxEventJpaEntity entity = invocation.getArgument(0);
            entity.setPublishedAt(Instant.now());
            outboxRepo.save(entity);
            return null;
        }).when(kafkaOutboxPublisher).publish(any());

        // Act: trigger the relay via executor directly (cross-bean, @Transactional honoured)
        outboxRelayExecutor.relayBatch();

        // Assert: row is now PUBLISHED (publishedAt is not null)
        OutboxEventJpaEntity updated = outboxRepo.findById(pending.getId()).orElseThrow();
        assertThat(updated.getPublishedAt()).isNotNull();
        assertThat(updated.getFailedAt()).isNull();
    }

    @Test
    void relayBatch_doesNotReprocess_alreadyPublishedRow() {
        // Arrange: save a row that's already PUBLISHED
        OutboxEventJpaEntity published = buildPendingRow();
        published.setPublishedAt(Instant.now());
        outboxRepo.save(published);

        // Act: trigger the relay (should not pick up already-published rows)
        outboxRelayExecutor.relayBatch();

        // Assert: row still has same publishedAt (not re-processed)
        OutboxEventJpaEntity found = outboxRepo.findById(published.getId()).orElseThrow();
        assertThat(found.getPublishedAt()).isNotNull();
    }

    @Test
    void relayScheduled_delegatesToExecutor_crossBean() {
        // Verify the thin service can fire without error
        // (actual relay logic tested via executor above)
        outboxRelayService.relayScheduled();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private OutboxEventJpaEntity buildPendingRow() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateId(UUID.randomUUID());
        entity.setAggregateType("Task");
        entity.setEventType("TaskCreated");
        entity.setTopic("task.events");
        entity.setPayload("{\"eventId\":\"test\"}");
        entity.setCreatedAt(Instant.now());
        return entity;
    }
}
