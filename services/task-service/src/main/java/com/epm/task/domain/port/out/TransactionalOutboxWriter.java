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
     * Atomically bulk-deletes the root task's subtasks, publishes the root's pending
     * domain events to the outbox, and deletes the root task row — ALL in one transaction.
     *
     * <p>Because everything happens inside a single {@code @Transactional} boundary on the
     * infrastructure implementation, a failure in any step rolls back all operations.
     * This prevents split-brain states where subtasks are deleted but the root task still
     * exists (or vice versa) because a subsequent step failed after a prior committed tx.
     *
     * <p>Callers (use cases) must NOT call {@code TaskRepository.bulkDeleteSubtasks} directly
     * before invoking this method — doing so would create a separate committed transaction
     * and break the atomicity guarantee.
     *
     * <p>The implementation should pull domain events from the aggregate BEFORE the
     * bulk-delete to ensure event data is captured within the same transaction.
     *
     * @param root the root task aggregate (with pending domain events raised by
     *             {@link Task#markDeleted()}) whose subtasks and row are to be deleted
     */
    void publishAndDeleteWithSubtasks(Task root);
}
