package com.epm.template.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when an Example is created.
 *
 * <p>Domain events are plain records — no framework dependencies.
 * Infrastructure adapters (outbox publisher) consume them.
 * The {@code tenantId} field allows the outbox envelope to populate
 * multi-tenant routing metadata without touching the domain model.
 */
public record ExampleCreatedEvent(
        UUID eventId,
        UUID exampleId,
        UUID tenantId,
        String name,
        Instant occurredAt) {

    /**
     * Convenience factory that generates the event identity and timestamp.
     *
     * @param exampleId the id of the created Example aggregate
     * @param tenantId  the tenant this example belongs to
     * @param name      the name of the created Example
     * @return a fully populated {@link ExampleCreatedEvent}
     */
    public static ExampleCreatedEvent of(UUID exampleId, UUID tenantId, String name) {
        return new ExampleCreatedEvent(UUID.randomUUID(), exampleId, tenantId, name, Instant.now());
    }
}
