package com.epm.auth.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.auth.domain.event.AccountRegisteredEvent;
import com.epm.auth.domain.port.out.DomainEventPublisher;
import com.epm.auth.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.auth.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Implements {@link DomainEventPublisher} port using the outbox pattern.
 *
 * <p>Saves each domain event as an {@link OutboxEventJpaEntity} row in the same
 * transaction as the aggregate. After the transaction commits, fires
 * {@link OutboxEventSavedEvent} to trigger near-real-time relay to Kafka.
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private static final String TOPIC_ACCOUNT_REGISTERED = "auth.account.registered";

    private final OutboxEventJpaRepository outboxRepo;
    private final ApplicationEventPublisher appEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxDomainEventPublisher(
            OutboxEventJpaRepository outboxRepo,
            ApplicationEventPublisher appEventPublisher,
            ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.appEventPublisher = appEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(List<Object> events) {
        for (Object event : events) {
            if (event instanceof AccountRegisteredEvent registered) {
                OutboxEventJpaEntity entity = buildOutboxEntity(registered);
                outboxRepo.save(entity);
                appEventPublisher.publishEvent(new OutboxEventSavedEvent(this));
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private OutboxEventJpaEntity buildOutboxEntity(AccountRegisteredEvent event) {
        String payload = buildEnvelopeJson(event);

        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UuidCreator.getTimeOrderedEpoch());
        entity.setAggregateId(event.accountId());
        entity.setAggregateType("Account");
        entity.setEventType("AccountRegistered");
        entity.setTopic(TOPIC_ACCOUNT_REGISTERED);
        entity.setPayload(payload);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private String buildEnvelopeJson(AccountRegisteredEvent event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "AccountRegistered");
            envelope.put("eventVersion", 1);
            envelope.put("occurredAt", event.occurredAt().toString());
            envelope.put("aggregateId", event.accountId().toString());
            envelope.put("aggregateType", "Account");
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("traceId", UUID.randomUUID().toString());

            ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("accountId", event.accountId().toString());
            if (event.keycloakUserId() != null) {
                payloadNode.put("keycloakUserId", event.keycloakUserId().toString());
            }
            payloadNode.put("email", event.email());
            payloadNode.put("firstName", event.firstName());
            payloadNode.put("lastName", event.lastName());

            envelope.set("payload", payloadNode);
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event", e);
        }
    }
}
