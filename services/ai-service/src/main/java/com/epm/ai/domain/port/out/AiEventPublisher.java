package com.epm.ai.domain.port.out;

import com.epm.ai.domain.event.AiTasksGenerated;

/**
 * Driven port: publish AI domain events (via outbox pattern).
 */
public interface AiEventPublisher {

    /**
     * Publishes an AiTasksGenerated event to the outbox for async relay to Kafka.
     */
    void publish(AiTasksGenerated event);
}
