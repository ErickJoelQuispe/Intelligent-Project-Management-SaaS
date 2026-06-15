package com.epm.task.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.epm.task.infrastructure.AbstractPostgresIT;
import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
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
    void pendingRow_isPublishedAndMarkedPublished() {
        OutboxEventJpaEntity pending = persist(newRow(), Instant.now(), null, null);

        executor.relayBatch();

        verify(publisher, times(1)).publish(any());
        OutboxEventJpaEntity reloaded = reload(pending.getId());
        assertThat(reloaded.getPublishedAt()).as("pending row must be published").isNotNull();
        assertThat(reloaded.getFailedAt()).isNull();
    }

    @Test
    void recentlyFailedRow_isNotRetried_withinCooldown() {
        persist(newRow(), Instant.now(), null, Instant.now());

        executor.relayBatch();

        verify(publisher, never()).publish(any());
    }

    @Test
    void oldFailedRow_isRetried_afterCooldown() {
        Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
        OutboxEventJpaEntity old = persist(newRow(), Instant.now(), null, tenMinutesAgo);

        executor.relayBatch();

        verify(publisher, times(1)).publish(any());
        OutboxEventJpaEntity reloaded = reload(old.getId());
        assertThat(reloaded.getPublishedAt()).as("old-failed row must be retried and published").isNotNull();
    }

    @Test
    void alreadyPublishedRow_isIgnored() {
        persist(newRow(), Instant.now(), Instant.now(), null);

        executor.relayBatch();

        verify(publisher, never()).publish(any());
    }

    @Test
    void publishFailure_setsFailedAtAndError_notPublishedAt() {
        OutboxEventJpaEntity pending = persist(newRow(), Instant.now(), null, null);
        doThrow(new RuntimeException("broker down")).when(publisher).publish(any());

        executor.relayBatch();

        verify(publisher, times(1)).publish(any());
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
        e.setAggregateType("Task");
        e.setEventType("TaskCreated");
        e.setTopic("task.events");
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
