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
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Implements {@link DomainEventPublisher} port using the outbox pattern.
 *
 * <p>Saves each domain event as an {@link OutboxEventJpaEntity} row in the same
 * transaction as the aggregate. After the transaction commits, fires
 * {@link OutboxEventSavedEvent} to trigger near-real-time relay to Kafka.
 *
 * <p>The outbox envelope includes the distributed {@code traceId} resolved from the
 * active Micrometer span, enabling end-to-end trace correlation between the HTTP
 * request and the Kafka event. When no active span is present (e.g. background jobs
 * or test slices without tracing auto-configuration), a random UUID is used as a
 * defensive fallback so the publisher never fails due to an absent tracer.
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private static final String TOPIC_ACCOUNT_REGISTERED = "auth.account.registered";

    private final OutboxEventJpaRepository outboxRepo;
    private final ApplicationEventPublisher appEventPublisher;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Tracer> tracerProvider;

    public OutboxDomainEventPublisher(
            OutboxEventJpaRepository outboxRepo,
            ApplicationEventPublisher appEventPublisher,
            ObjectMapper objectMapper,
            ObjectProvider<Tracer> tracerProvider) {
        this.outboxRepo = outboxRepo;
        this.appEventPublisher = appEventPublisher;
        this.objectMapper = objectMapper;
        this.tracerProvider = tracerProvider;
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

    /**
     * Resolves the current distributed trace ID from the active Micrometer span.
     *
     * <p>Returns the trace ID of the active span when one is present. Falls back to
     * a random UUID when tracing is disabled, the span is absent, or the Tracer bean
     * is not available in the application context (e.g. lightweight test slices).
     */
    private String resolveTraceId() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer != null && tracer.currentSpan() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return UUID.randomUUID().toString();
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
            envelope.put("traceId", resolveTraceId());

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
