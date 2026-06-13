package com.epm.template.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.template.domain.event.ExampleCreatedEvent;
import com.epm.template.domain.port.out.ExampleEventPublisher;
import com.epm.template.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.template.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Outbox implementation of {@link ExampleEventPublisher}.
 *
 * <p>For each domain event, builds a JSON envelope and inserts an {@link OutboxEventJpaEntity}
 * row — in the SAME transaction already open for the aggregate save (managed by
 * {@link com.epm.template.infrastructure.adapter.out.persistence.TransactionalExampleWriterImpl}).
 *
 * <p>After saving the row, fires a Spring {@link OutboxEventSavedEvent} so the relay
 * can pick it up near-real-time via {@code @TransactionalEventListener(AFTER_COMMIT)}.
 *
 * <p>The JSON envelope format is:
 * <pre>
 * {
 *   "eventId":       "...",
 *   "eventType":     "ExampleCreated",
 *   "eventVersion":  1,
 *   "occurredAt":    "...",
 *   "aggregateId":   "...",
 *   "aggregateType": "Example",
 *   "tenantId":      "...",
 *   "traceId":       "...",
 *   "payload": { "exampleId": "...", "name": "..." }
 * }
 * </pre>
 */
@Component
class OutboxEventPublisherAdapter implements ExampleEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisherAdapter.class);
    private static final String TOPIC_EXAMPLE_CREATED = "template.example.created";
    // Schema version is conceptually owned by each event type; it lives here as a constant
    // for simplicity. Bump it whenever the envelope/payload schema for this event changes.
    private static final int EVENT_VERSION = 1;

    private final OutboxEventJpaRepository outboxRepo;
    private final ApplicationEventPublisher appEventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Injected {@code @Nullable} on purpose: this template ships micrometer-tracing-bridge-otel,
     * so a real Tracer bean is present and {@link #resolveTraceId()} reads the active span's
     * trace ID. But a derived service may drop micrometer-tracing-bridge-otel from its pom — in
     * that case no Tracer bean exists, Spring injects {@code null} here, and the adapter still
     * works because {@link #resolveTraceId()} falls back to a random UUID. No code change needed.
     */
    private final Tracer tracer;

    OutboxEventPublisherAdapter(
            OutboxEventJpaRepository outboxRepo,
            ApplicationEventPublisher appEventPublisher,
            ObjectMapper objectMapper,
            @Nullable Tracer tracer) {
        this.outboxRepo = outboxRepo;
        this.appEventPublisher = appEventPublisher;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    @Override
    public void publish(List<Object> events) {
        for (Object event : events) {
            if (event instanceof ExampleCreatedEvent e) {
                OutboxEventJpaEntity entity = buildExampleCreatedEntity(e);
                outboxRepo.save(entity);
                appEventPublisher.publishEvent(new OutboxEventSavedEvent(this));
                log.debug("Outbox row saved: eventType=ExampleCreated aggregateId={}", e.exampleId());
            } else {
                log.warn("OutboxEventPublisherAdapter: unrecognised event type {}", event.getClass().getName());
            }
        }
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private OutboxEventJpaEntity buildExampleCreatedEntity(ExampleCreatedEvent event) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateId(event.exampleId());
        entity.setAggregateType("Example");
        entity.setEventType("ExampleCreated");
        entity.setTopic(TOPIC_EXAMPLE_CREATED);
        entity.setPayload(buildExampleCreatedPayload(event));
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private String buildExampleCreatedPayload(ExampleCreatedEvent event) {
        try {
            String traceId = resolveTraceId();

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", "ExampleCreated");
            envelope.put("eventVersion", EVENT_VERSION);
            envelope.put("occurredAt", event.occurredAt().toString());
            envelope.put("aggregateId", event.exampleId().toString());
            envelope.put("aggregateType", "Example");
            envelope.put("tenantId", event.tenantId().toString());
            envelope.put("traceId", traceId);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("exampleId", event.exampleId().toString());
            payload.put("name", event.name());
            envelope.set("payload", payload);

            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ExampleCreatedEvent", e);
        }
    }

    /**
     * Resolves the current trace ID from Micrometer Tracer when available;
     * falls back to a random UUID otherwise.
     */
    private String resolveTraceId() {
        if (tracer != null && tracer.currentSpan() != null) {
            return tracer.currentSpan().context().traceId();
        }
        // Fallback: micrometer-tracing not active or no active span
        return UUID.randomUUID().toString();
    }
}
