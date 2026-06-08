package com.epm.project.domain.port.out;

import java.util.List;

/**
 * Driven port for publishing domain events.
 */
public interface DomainEventPublisher {

    void publish(List<Object> events);
}
