package com.epm.auth.domain.port.out;

import java.util.List;

/**
 * Driven port: publishes domain events via the outbox pattern.
 *
 * <p>Implemented by the infrastructure layer (OutboxDomainEventPublisher).
 * Called by use cases after persisting the aggregate.
 */
public interface DomainEventPublisher {

    /**
     * Persists domain events as outbox rows in the same transaction as the aggregate.
     * The outbox relay will publish them to Kafka after the transaction commits.
     *
     * @param events the domain events to publish (must not be null)
     */
    void publish(List<Object> events);
}
