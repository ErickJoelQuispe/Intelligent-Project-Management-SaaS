package com.epm.template.domain.port.out;

import com.epm.template.domain.model.Example;

/**
 * Driven port: persists an {@link Example} aggregate and its pulled domain events
 * atomically — both operations commit or roll back together within ONE transaction.
 *
 * <p>This is the canonical save-and-publish contract. Use cases depend on this
 * port instead of calling {@link ExampleRepository} and {@link ExampleEventPublisher}
 * separately, which would NOT be atomic and could lose events on a mid-flight failure.
 *
 * <p>The infrastructure implementation ({@code TransactionalExampleWriterImpl}) is
 * annotated {@code @Transactional} and performs the operations in this order:
 * <ol>
 *   <li>Pull domain events from the aggregate (before save, to avoid event loss on reconstitution).
 *   <li>Save the aggregate via {@link ExampleRepository}.
 *   <li>Publish the events via {@link ExampleEventPublisher} (inserts outbox rows).
 * </ol>
 */
public interface TransactionalExampleWriter {

    /**
     * Saves the Example aggregate and publishes its pending domain events in one transaction.
     *
     * @param example the aggregate whose events (from {@code pullDomainEvents()}) will be persisted
     * @return the saved Example as returned by the repository
     */
    Example saveAndPublish(Example example);
}
