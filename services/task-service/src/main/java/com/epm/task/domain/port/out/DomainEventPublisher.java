package com.epm.task.domain.port.out;

import java.util.List;

/**
 * Driven port for publishing domain events (e.g., to the outbox).
 */
public interface DomainEventPublisher {

    void publish(List<Object> events);
}
