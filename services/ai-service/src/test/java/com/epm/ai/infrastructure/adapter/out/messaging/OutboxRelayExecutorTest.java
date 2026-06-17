package com.epm.ai.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OutboxRelayExecutor}.
 *
 * <p>Verifies pending events are relayed via the SKIP-LOCKED claim query (H1) and
 * that poison events past {@link OutboxRelayExecutor#MAX_ATTEMPTS} are parked rather
 * than retried forever (H3).
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayExecutorTest {

    @Mock
    private OutboxEventJpaRepository outboxRepo;

    @Mock
    private KafkaOutboxPublisher kafkaOutboxPublisher;

    @InjectMocks
    private OutboxRelayExecutor executor;

    // ── pending event is relayed via the SKIP-LOCKED claim query ──────────────

    @Test
    void relayPending_publishesPendingEvent() {
        OutboxEventJpaEntity pending = buildPendingRow();
        when(outboxRepo.lockPendingForRelay()).thenReturn(List.of(pending));

        executor.relayPending();

        verify(kafkaOutboxPublisher).publish(pending);
    }

    @Test
    void relayPending_doesNothing_whenNoPendingEvents() {
        when(outboxRepo.lockPendingForRelay()).thenReturn(Collections.emptyList());

        executor.relayPending();

        verify(kafkaOutboxPublisher, never()).publish(any());
    }

    // ── H3: poison event past MAX_ATTEMPTS is parked, NOT retried ──────────────

    @Test
    void retryFailed_parksPoisonEvent_atMaxAttempts() {
        OutboxEventJpaEntity poison = buildPendingRow();
        poison.setFailedAt(Instant.now().minusSeconds(60));
        poison.setAttempts(OutboxRelayExecutor.MAX_ATTEMPTS);
        when(outboxRepo.lockFailedForRetry(any())).thenReturn(List.of(poison));

        executor.retryFailed();

        verify(kafkaOutboxPublisher, never()).publish(any());
        verify(outboxRepo).save(poison);
        assertThat(poison.isParked()).as("poison event must be parked").isTrue();
    }

    // ── H3: event below MAX_ATTEMPTS is retried ───────────────────────────────

    @Test
    void retryFailed_retriesEvent_belowMaxAttempts() {
        OutboxEventJpaEntity retryable = buildPendingRow();
        retryable.setFailedAt(Instant.now().minusSeconds(60));
        retryable.setAttempts(OutboxRelayExecutor.MAX_ATTEMPTS - 1);
        when(outboxRepo.lockFailedForRetry(any())).thenReturn(List.of(retryable));

        executor.retryFailed();

        verify(kafkaOutboxPublisher).publish(retryable);
        assertThat(retryable.isParked()).isFalse();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private OutboxEventJpaEntity buildPendingRow() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateId(UUID.randomUUID());
        entity.setAggregateType("AiEvent");
        entity.setEventType("AiTasksGenerated");
        entity.setTopic("ai.events");
        entity.setPayload("{\"eventId\":\"test\"}");
        entity.setCreatedAt(Instant.now());
        return entity;
    }
}
