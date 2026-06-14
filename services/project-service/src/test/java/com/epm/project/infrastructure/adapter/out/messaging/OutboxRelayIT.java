package com.epm.project.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.epm.project.infrastructure.AbstractPostgresIT;
import com.epm.project.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.project.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test that exercises the relay's native {@code FOR UPDATE SKIP LOCKED}
 * queries ({@code lockPendingBatch} / {@code lockRetryBatch}) against a REAL Testcontainers
 * PostgreSQL database — the whole point being that the SQL is actually parsed and run by
 * Postgres (H2 cannot), proving the SKIP LOCKED relay logic works end-to-end.
 *
 * <p>{@link KafkaOutboxPublisher} is mocked because there is no real Kafka broker in this
 * test; we only assert the relay's DB-state transitions (published_at / failed_at) and that
 * {@code publish} is (or is not) invoked per the batch-selection predicates.
 *
 * <p>The {@code @Scheduled} relay is disabled so it does not fire concurrently and race the
 * explicit {@code executor.relayBatch()} calls under test.
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
class OutboxRelayIT extends AbstractPostgresIT {

    @Autowired
    private OutboxRelayExecutor executor;

    @Autowired
    private OutboxEventJpaRepository outboxRepo;

    @MockitoBean
    private KafkaOutboxPublisher publisher;

    @AfterEach
    void cleanup() {
        outboxRepo.deleteAll();
    }

    @Test
    void pendingRow_isPublished_andMarkedPublished() {
        OutboxEventJpaEntity pending = persist(newRow(), Instant.now(), null, null);

        executor.relayBatch();

        verify(publisher, times(1))
                .publish(eq(pending.getTopic()), eq(pending.getAggregateId().toString()), eq(pending.getPayload()));
        OutboxEventJpaEntity reloaded = reload(pending.getId());
        assertThat(reloaded.getPublishedAt()).as("pending row must be published").isNotNull();
        assertThat(reloaded.getFailedAt()).isNull();
    }

    @Test
    void recentlyFailedRow_isNotRetried_withinCooldown() {
        OutboxEventJpaEntity recent = persist(newRow(), Instant.now(), null, Instant.now());

        executor.relayBatch();

        verify(publisher, never()).publish(anyString(), anyString(), anyString());
        OutboxEventJpaEntity reloaded = reload(recent.getId());
        assertThat(reloaded.getPublishedAt()).as("recently-failed row must stay unpublished").isNull();
    }

    @Test
    void oldFailedRow_isRetried_afterCooldown() {
        Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
        OutboxEventJpaEntity old = persist(newRow(), Instant.now(), null, tenMinutesAgo);

        executor.relayBatch();

        verify(publisher, times(1))
                .publish(eq(old.getTopic()), eq(old.getAggregateId().toString()), eq(old.getPayload()));
        OutboxEventJpaEntity reloaded = reload(old.getId());
        assertThat(reloaded.getPublishedAt()).as("old-failed row must be retried and published").isNotNull();
    }

    @Test
    void alreadyPublishedRow_isIgnored() {
        persist(newRow(), Instant.now(), Instant.now(), null);

        executor.relayBatch();

        verify(publisher, never()).publish(anyString(), anyString(), anyString());
    }

    @Test
    void publishFailure_setsFailedAtAndError_notPublishedAt() {
        OutboxEventJpaEntity pending = persist(newRow(), Instant.now(), null, null);
        doThrow(new RuntimeException("broker down")).when(publisher).publish(anyString(), anyString(), anyString());

        executor.relayBatch();

        verify(publisher, times(1)).publish(anyString(), anyString(), anyString());
        OutboxEventJpaEntity reloaded = reload(pending.getId());
        assertThat(reloaded.getPublishedAt()).as("failed publish must not set published_at").isNull();
        assertThat(reloaded.getFailedAt()).as("failed publish must set failed_at").isNotNull();
        assertThat(reloaded.getError()).contains("broker down");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static OutboxEventJpaEntity newRow() {
        OutboxEventJpaEntity e = new OutboxEventJpaEntity();
        e.setId(UUID.randomUUID());
        e.setAggregateId(UUID.randomUUID());
        e.setAggregateType("Project");
        e.setEventType("ProjectCreated");
        e.setTopic("project.project.created");
        e.setPayload("{\"k\":\"v\"}");
        return e;
    }

    private OutboxEventJpaEntity persist(OutboxEventJpaEntity e, Instant createdAt,
            Instant publishedAt, Instant failedAt) {
        e.setCreatedAt(createdAt);
        e.setPublishedAt(publishedAt);
        e.setFailedAt(failedAt);
        return outboxRepo.save(e);
    }

    private OutboxEventJpaEntity reload(UUID id) {
        return outboxRepo.findById(id).orElseThrow();
    }
}
