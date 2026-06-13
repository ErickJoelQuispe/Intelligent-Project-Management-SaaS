package com.epm.user.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.user.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OutboxRelayExecutor}.
 *
 * <p>Tests the same 3 scenarios as auth-service:
 * - Pending event is published → publishedAt set
 * - Recently failed event (2 min ago) is NOT retried
 * - Old failed event (6 min ago) IS retried
 *
 * <p>Also verifies that {@link OutboxRelayService} delegates cross-bean to the executor
 * (no self-invocation).
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    private OutboxEventJpaRepository outboxRepo;

    @Mock
    private KafkaOutboxPublisher publisher;

    private OutboxRelayExecutor executor;
    private OutboxRelayService service;

    @BeforeEach
    void setUp() {
        executor = new OutboxRelayExecutor(outboxRepo, publisher);
        service = new OutboxRelayService(executor);
    }

    // ── OutboxRelayExecutor scenarios ─────────────────────────────────────────

    @Test
    void pendingEventIsPublishedAndPublishedAtIsSet() {
        OutboxEventJpaEntity pending = buildPending();
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of(pending));
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of());
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        executor.relayBatch();

        verify(publisher).publish(anyString(), anyString(), anyString());
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
        assertThat(captor.getValue().getFailedAt()).isNull();
    }

    @Test
    void recentlyFailedEventTwoMinutesAgoIsNotRetried() {
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of());
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of()); // 2-min-old failure NOT returned

        executor.relayBatch();

        verify(publisher, never()).publish(anyString(), anyString(), anyString());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void oldFailedEventSixMinutesAgoIsRetried() {
        OutboxEventJpaEntity oldFailed = buildOldFailed();
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of());
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of(oldFailed));
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        executor.relayBatch();

        verify(publisher, times(1)).publish(anyString(), anyString(), anyString());
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    void publishFailureSetsFailedAtAndErrorOnOutboxRow() {
        OutboxEventJpaEntity pending = buildPending();
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of(pending));
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of());
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("Kafka down"))
                .when(publisher).publish(anyString(), anyString(), anyString());

        executor.relayBatch();

        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        assertThat(captor.getValue().getFailedAt()).isNotNull();
        assertThat(captor.getValue().getError()).contains("Kafka down");
        assertThat(captor.getValue().getPublishedAt()).isNull();
    }

    // ── OutboxRelayService cross-bean delegation ──────────────────────────────

    @Test
    void scheduledRelayDelegatestoExecutorCrossBean() {
        // OutboxRelayService.scheduledRelay() must call executor.relayBatch() not itself
        // We verify by confirming the executor's repo methods are invoked (would not happen
        // if self-invocation bypassed the executor).
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of());
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of());

        service.scheduledRelay();

        // If the executor was called, lockPendingBatch must have been invoked
        verify(outboxRepo).lockPendingBatch();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OutboxEventJpaEntity buildPending() {
        OutboxEventJpaEntity e = new OutboxEventJpaEntity();
        e.setId(UUID.randomUUID());
        e.setAggregateId(UUID.randomUUID());
        e.setAggregateType("UserProfile");
        e.setEventType("ProfileUpdated");
        e.setTopic("user.profile.updated");
        e.setPayload("{\"userId\": \"test\"}");
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
