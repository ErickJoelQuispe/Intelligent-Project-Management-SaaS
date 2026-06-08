package com.epm.template.domain.port.out;

import com.epm.template.domain.event.ExampleCreatedEvent;

/**
 * Driven port: event publishing contract defined by the domain.
 *
 * <p>The domain fires events without knowing whether they go to Kafka,
 * an in-memory bus, or a test spy. The adapter decides.
 */
public interface ExampleEventPublisher {

    void publish(ExampleCreatedEvent event);
}
