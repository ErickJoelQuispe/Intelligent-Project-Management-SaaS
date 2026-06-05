package com.epm.ai.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.UUID;

import com.epm.ai.domain.event.AiTasksGenerated;
import com.epm.ai.domain.port.out.AiEventPublisher;
import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link AiEventPublisher} using the outbox pattern.
 *
 * <p>Persists {@link AiTasksGenerated} events to the {@code outbox_events} table
 * in the same transaction as the caller. Fires {@link OutboxEventSavedEvent}
 * to trigger immediate relay after commit.
 */
@Component
public class OutboxAiEventPublisher implements AiEventPublisher {

    static final String TOPIC_AI_EVENTS = "ai.events";

    private final OutboxEventJpaRepository outboxRepo;
    private final ApplicationEventPublisher appEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxAiEventPublisher(OutboxEventJpaRepository outboxRepo,
                                  ApplicationEventPublisher appEventPublisher,
                                  ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.appEventPublisher = appEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(AiTasksGenerated event) {
        String payload = buildPayload(event);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UuidCreator.getTimeOrderedEpoch());
        entity.setAggregateId(toUuid(event.projectId()));
        entity.setAggregateType("AiEvent");
        entity.setEventType("AiTasksGenerated");
        entity.setTopic(TOPIC_AI_EVENTS);
        entity.setPayload(payload);
        entity.setCreatedAt(Instant.now());

        outboxRepo.save(entity);
        appEventPublisher.publishEvent(new OutboxEventSavedEvent(this));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildPayload(AiTasksGenerated event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            UUID rawId = event.eventId();
            String eventId = rawId != null ? rawId.toString() : UuidCreator.getTimeOrderedEpoch().toString();
            envelope.put("eventId", eventId);
            envelope.put("eventType", "AiTasksGenerated");
            envelope.put("tenantId", event.tenantId());
            String occurredAt = event.occurredAt() != null ? event.occurredAt().toString() : Instant.now().toString();
            envelope.put("occurredAt", occurredAt);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("projectId", event.projectId());
            payload.put("generatedBy", event.generatedBy());
            payload.set("tasks", objectMapper.valueToTree(event.tasks()));
            envelope.set("payload", payload);

            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize AiTasksGenerated event", ex);
        }
    }

    private java.util.UUID toUuid(String value) {
        if (value == null) {
            return UuidCreator.getTimeOrderedEpoch();
        }
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            // If projectId is not a UUID, generate a deterministic UUID from the string
            return java.util.UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
