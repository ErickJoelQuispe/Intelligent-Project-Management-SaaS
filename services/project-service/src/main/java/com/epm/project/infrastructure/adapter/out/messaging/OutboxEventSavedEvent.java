package com.epm.project.infrastructure.adapter.out.messaging;

import org.springframework.context.ApplicationEvent;

/**
 * Spring application event published after an outbox row is persisted.
 *
 * <p>Triggers {@link OutboxRelayService} to attempt immediate relay via Kafka.
 */
public class OutboxEventSavedEvent extends ApplicationEvent {

    public OutboxEventSavedEvent(Object source) {
        super(source);
    }
}
