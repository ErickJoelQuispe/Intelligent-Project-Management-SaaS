package com.epm.task.domain.port.out;

import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;

/**
 * Driven port: persists the {@link Task} aggregate, its pending domain events,
 * and an optional {@link ActivityLog} atomically in ONE transaction using the
 * outbox pattern.
 *
 * <p>Implementations must save the aggregate, publish its events to the outbox,
 * and (if non-null) save the activity log — all in a single transaction so that
 * a failure in any step rolls back all, preventing split-brain states.
 *
 * <p>Domain and application layers remain free of Spring/JPA/Jackson/Kafka imports
 * — this port is a plain Java interface wired by {@code UseCaseConfig}.
 */
public interface TransactionalOutboxWriter {

    /**
     * Saves the task aggregate, publishes its pulled domain events to the outbox,
     * and saves the activity log (if non-null) — all within one transaction.
     *
     * @param task        the aggregate whose state and pending events are to be persisted
     * @param activityLog the activity log entry to save alongside the task (nullable)
     * @return the reloaded, persisted task (as returned by the persistence adapter)
     */
    Task saveAndPublish(Task task, ActivityLog activityLog);

    /**
     * Publishes the task's pending domain events to the outbox and then deletes
     * the task aggregate — all within one transaction.
     *
     * <p>Used for the delete flow where the task is marked deleted (events are raised)
     * but the row must be removed rather than updated.
     *
     * @param task the aggregate whose events are to be published before deletion
     */
    void publishAndDelete(Task task);
}
