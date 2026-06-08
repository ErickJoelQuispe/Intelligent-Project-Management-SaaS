package com.epm.template.infrastructure.adapter.out.messaging;

import com.epm.template.domain.event.ExampleCreatedEvent;
import com.epm.template.domain.port.out.ExampleEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Messaging adapter: implements the domain's event publishing port.
 *
 * <p>In Phase 0 this logs the event. In production phases this will
 * write to the outbox table and a Kafka producer will relay it.
 * The domain use case is completely unaware of this change.
 */
@Component
class ExampleEventPublisherAdapter implements ExampleEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ExampleEventPublisherAdapter.class);

    @Override
    public void publish(ExampleCreatedEvent event) {
        // TODO Phase 1+: replace with outbox table insert inside the same transaction
        log.info(
                "Publishing event: type={} aggregateId={} occurredAt={}",
                event.getClass().getSimpleName(),
                event.exampleId(),
                event.occurredAt());
    }
}
