package com.epm.auth.infrastructure.adapter.out.messaging;

/**
 * Spring ApplicationEvent fired after an outbox row is saved to the database.
 *
 * <p>Triggers {@link OutboxRelayService#onOutboxEventSaved(OutboxEventSavedEvent)}
 * via {@code @TransactionalEventListener(phase = AFTER_COMMIT)} to initiate
 * near-real-time relay to Kafka without polling delay.
 *
 * @param source the object that published this event (typically the DomainEventPublisher adapter)
 */
public record OutboxEventSavedEvent(Object source) {
}
