package com.epm.ai.infrastructure.adapter.out.messaging;

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
 * Unit tests for {@link OutboxRelayService}.
 * Verifies pending events are relayed to Kafka and already-published rows are skipped.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    private OutboxEventJpaRepository outboxRepo;

    @Mock
    private KafkaOutboxPublisher kafkaOutboxPublisher;

    @InjectMocks
    private OutboxRelayService outboxRelayService;

    // ── pending event is relayed ──────────────────────────────────────────────

    @Test
    void relayScheduled_publishesPendingEvent() {
        OutboxEventJpaEntity pending = buildPendingRow();
        when(outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(pending));
        when(outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtBeforeOrderByCreatedAtAsc(any()))
                .thenReturn(Collections.emptyList());

        outboxRelayService.relayScheduled();

        verify(kafkaOutboxPublisher).publish(pending);
    }

    // ── no pending events → nothing published ────────────────────────────────

    @Test
    void relayScheduled_doesNothing_whenNoPendingEvents() {
        when(outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(Collections.emptyList());
        when(outboxRepo.findTop10ByPublishedAtIsNullAndFailedAtBeforeOrderByCreatedAtAsc(any()))
                .thenReturn(Collections.emptyList());

        outboxRelayService.relayScheduled();

        verify(kafkaOutboxPublisher, never()).publish(any());
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
