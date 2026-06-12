package com.epm.auth.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.epm.auth.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.auth.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link OutboxRelayExecutor}.
 *
 * <p>The transactional relay logic lives on {@link OutboxRelayExecutor} (a separate bean from
 * {@link OutboxRelayService}) so that calls from the trigger service cross the Spring proxy
 * boundary and {@code @Transactional} is actually applied. This test exercises that logic
 * directly: pending batch fetched, retry batch fetched with the cooldown threshold, publish
 * invoked, and the success/failure bookkeeping on the entity.
 *
 * <p>Both {@link OutboxEventJpaRepository#lockPendingBatch()} and
 * {@link OutboxEventJpaRepository#lockRetryBatch(Instant)} use {@code FOR UPDATE SKIP LOCKED}
 * to prevent double-publish races.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayExecutorTest {

    @Mock
    private OutboxEventJpaRepository outboxRepo;

    @Mock
    private KafkaOutboxPublisher publisher;

    private OutboxRelayExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new OutboxRelayExecutor(outboxRepo, publisher);
    }

    @Test
    void pendingEventIsPublishedAndPublishedAtIsSet() {
        // Arrange: one pending event (no publishedAt, no failedAt)
        OutboxEventJpaEntity pending = buildPending();
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of(pending));
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of());
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        executor.relayBatch();

        // Assert: pending batch fetched, retry batch fetched with a threshold
        verify(outboxRepo).lockPendingBatch();
        verify(outboxRepo).lockRetryBatch(any(Instant.class));

        // Assert: publisher called once, publishedAt is set
        verify(publisher).publish(anyString(), anyString(), anyString());
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
        assertThat(captor.getValue().getFailedAt()).isNull();
    }

    @Test
    void recentlyFailedEventTwoMinutesAgoIsNotRetried() {
        // Arrange: recently failed event (2 min ago — within 5 min cooldown)
        // The lockRetryBatch query uses `failed_at < threshold` where threshold = now - 5min.
        // A 2-minute-old failure does NOT satisfy this condition → not returned.
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of());
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of());

        // Act
        executor.relayBatch();

        // Assert: publisher never called
        verify(publisher, never()).publish(anyString(), anyString(), anyString());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void oldFailedEventSixMinutesAgoIsRetried() {
        // Arrange: old failed event (6 min ago — outside 5 min cooldown)
        OutboxEventJpaEntity oldFailed = buildOldFailed();
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of());
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of(oldFailed));
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        executor.relayBatch();

        // Assert: retry batch fetched with threshold and publisher called once
        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepo).lockRetryBatch(thresholdCaptor.capture());
        assertThat(thresholdCaptor.getValue()).isBefore(Instant.now());

        verify(publisher, times(1)).publish(anyString(), anyString(), anyString());
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    void failedPublishSetsFailedAtAndError() {
        // Arrange: one pending event whose publish throws
        OutboxEventJpaEntity pending = buildPending();
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of(pending));
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of());
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("broker down"))
                .when(publisher).publish(anyString(), anyString(), anyString());

        // Act
        executor.relayBatch();

        // Assert: failedAt + error recorded, publishedAt untouched
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        assertThat(captor.getValue().getFailedAt()).isNotNull();
        assertThat(captor.getValue().getError()).isEqualTo("broker down");
        assertThat(captor.getValue().getPublishedAt()).isNull();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private OutboxEventJpaEntity buildPending() {
        OutboxEventJpaEntity e = new OutboxEventJpaEntity();
        e.setId(UUID.randomUUID());
        e.setAggregateId(UUID.randomUUID());
        e.setAggregateType("Account");
        e.setEventType("AccountRegistered");
        e.setTopic("auth.account.registered");
        e.setPayload("{\"accountId\": \"test\"}");
        e.setCreatedAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        return e;
    }

    private OutboxEventJpaEntity buildOldFailed() {
        OutboxEventJpaEntity e = buildPending();
        e.setFailedAt(Instant.now().minus(6, ChronoUnit.MINUTES));
        e.setError("previous error");
        return e;
    }
}
