package com.epm.template.domain.port.out;

import java.util.List;

/**
 * Driven port: event publishing contract defined by the domain.
 *
 * <p>The domain fires events without knowing whether they go to Kafka,
 * an in-memory bus, or a test spy. The adapter decides.
 *
 * <p>Accepts {@code List<Object>} so the same port can carry any domain-event
 * type. Infrastructure implementations use {@code instanceof} pattern-matching
 * to dispatch each event to its corresponding outbox envelope builder.
 */
public interface ExampleEventPublisher {

    /**
     * Persists domain events as outbox rows in the same transaction as the aggregate.
     * The outbox relay will forward them to Kafka after the transaction commits.
     *
     * @param events the domain events to publish (must not be null; may be empty)
     */
    void publish(List<Object> events);
}
