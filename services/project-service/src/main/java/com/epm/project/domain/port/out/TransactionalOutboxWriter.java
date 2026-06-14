package com.epm.project.domain.port.out;

import com.epm.project.domain.model.Project;

/**
 * Driven port: persists the {@link Project} aggregate and its pending domain events
 * atomically (within one transaction) using the outbox pattern.
 *
 * <p>Implementations must save the aggregate and publish its events in a single
 * transaction so that a failure in either step rolls back both, preventing
 * split-brain (aggregate saved but no outbox row) or ghost events (outbox row
 * without an aggregate).
 *
 * <p>Domain and application layers remain free of Spring/JPA/Jackson/Kafka imports
 * — this port is a plain Java interface wired by {@code UseCaseConfig}.
 */
public interface TransactionalOutboxWriter {

    /**
     * Saves the project aggregate and publishes its pending domain events to the
     * outbox — both operations commit or roll back in the same transaction.
     *
     * @param project the aggregate whose state and pending events are to be persisted
     * @return the reloaded, persisted project (as returned by the persistence adapter)
     */
    Project saveAndPublish(Project project);
}
