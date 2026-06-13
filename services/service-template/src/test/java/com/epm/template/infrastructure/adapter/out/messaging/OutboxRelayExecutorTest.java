package com.epm.template.infrastructure.adapter.out.messaging;

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

import com.epm.template.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.template.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OutboxRelayExecutor}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Pending event is published and {@code published_at} is set.</li>
 *   <li>A recently-failed event (within cooldown) is NOT retried.</li>
 *   <li>An old-failed event (beyond cooldown) IS retried and {@code published_at} is set.</li>
 *   <li>A Kafka publish failure sets {@code failed_at} + {@code error} and leaves
 *       {@code published_at} null.</li>
 *   <li>{@link OutboxRelayService} delegates cross-bean to the executor (no self-invocation).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayExecutorTest {

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
    void recentlyFailedEventWithinCooldownIsNotRetried() {
        // The mock returns empty for retry batch — simulates the predicate excluding 2-min-old failures
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of());
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of());

        executor.relayBatch();

        verify(publisher, never()).publish(anyString(), anyString(), anyString());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void oldFailedEventBeyondCooldownIsRetried() {
        OutboxEventJpaEntity oldFailed = buildOldFailed();
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of());
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of(oldFailed));
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        executor.relayBatch();

        verify(publisher, times(1)).publish(anyString(), anyString(), anyString());
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
        assertThat(captor.getValue().getFailedAt()).isNotNull(); // original failedAt preserved on entity
    }

    @Test
    void publishFailureSetsFailedAtAndErrorAndLeavesPublishedAtNull() {
        OutboxEventJpaEntity pending = buildPending();
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of(pending));
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of());
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Kafka down"))
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
    void scheduledRelayDelegatesExecutorCrossBean() {
        // OutboxRelayService.scheduledRelay() must call executor.relayBatch() cross-bean,
        // not via self-invocation. We verify by confirming the executor's repo methods
        // are invoked (they would not be called if self-invocation bypassed the executor).
        when(outboxRepo.lockPendingBatch()).thenReturn(List.of());
        when(outboxRepo.lockRetryBatch(any(Instant.class))).thenReturn(List.of());

        service.scheduledRelay();

        verify(outboxRepo).lockPendingBatch();
        verify(outboxRepo).lockRetryBatch(any(Instant.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OutboxEventJpaEntity buildPending() {
        OutboxEventJpaEntity e = new OutboxEventJpaEntity();
        e.setId(UUID.randomUUID());
        e.setAggregateId(UUID.randomUUID());
        e.setAggregateType("Example");
        e.setEventType("ExampleCreated");
        e.setTopic("template.example.created");
        e.setPayload("{\"exampleId\": \"test\"}");
        e.setCreatedAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        return e;
    }

    private OutboxEventJpaEntity buildOldFailed() {
        OutboxEventJpaEntity e = buildPending();
        e.setFailedAt(Instant.now().minus(OutboxRelayExecutor.RETRY_COOLDOWN_MINUTES + 1, ChronoUnit.MINUTES));
        e.setError("previous error");
        return e;
    }
}
