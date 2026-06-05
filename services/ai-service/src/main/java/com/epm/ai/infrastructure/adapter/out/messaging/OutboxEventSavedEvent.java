package com.epm.ai.infrastructure.adapter.out.messaging;

import org.springframework.context.ApplicationEvent;

/**
 * Spring application event published after an outbox row is saved.
 *
 * <p>Triggers {@link OutboxRelayService#onOutboxEventSaved} via
 * {@link org.springframework.transaction.event.TransactionalEventListener} after commit.
 */
public class OutboxEventSavedEvent extends ApplicationEvent {

    public OutboxEventSavedEvent(Object source) {
        super(source);
    }
}
