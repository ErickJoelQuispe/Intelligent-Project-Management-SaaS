package com.epm.template.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when an Example is created.
 *
 * <p>Domain events are plain records — no framework dependencies.
 * Infrastructure adapters (Kafka producers, outbox) consume them.
 */
public record ExampleCreatedEvent(UUID eventId, UUID exampleId, String name, Instant occurredAt) {

    public static ExampleCreatedEvent of(UUID exampleId, String name) {
        return new ExampleCreatedEvent(UUID.randomUUID(), exampleId, name, Instant.now());
    }
}
