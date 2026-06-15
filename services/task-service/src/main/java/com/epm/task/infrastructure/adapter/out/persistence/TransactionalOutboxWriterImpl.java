package com.epm.task.infrastructure.adapter.out.persistence;

import java.util.List;

import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Infrastructure implementation of {@link TransactionalOutboxWriter}.
 *
 * <p>Annotated {@link Transactional} so that aggregate persistence, outbox event
 * insertion, and activity log persistence happen atomically. A failure in any step
 * rolls back all, preventing split-brain states.
 *
 * <p><strong>Event ordering:</strong> domain events are pulled from the aggregate
 * BEFORE calling {@link TaskRepository#save(Task)}. The
 * {@link TaskPersistenceAdapter#save(Task)} implementation reconstitutes a fresh
 * aggregate by reloading from the database — the in-memory event list on the original
 * aggregate instance would therefore be lost after the save returns. Pulling first
 * ensures the captured events are published to the outbox within the same transaction.
 * This mirrors the pattern established in {@code project-service/TransactionalOutboxWriterImpl}.
 *
 * <p><strong>Transaction propagation:</strong> {@link TaskPersistenceAdapter#bulkDeleteSubtasks}
 * and {@link TaskPersistenceAdapter#deleteByIdAndTenantId} are themselves annotated
 * {@code @Transactional} with the default {@code REQUIRED} propagation — they join the
 * existing transaction opened by this class rather than creating a new one. There is no
 * {@code REQUIRES_NEW} on those methods.
 */
@Component
public class TransactionalOutboxWriterImpl implements TransactionalOutboxWriter {

    private final TaskRepository taskRepository;
    private final DomainEventPublisher eventPublisher;
    private final ActivityLogRepository activityLogRepository;

    public TransactionalOutboxWriterImpl(TaskRepository taskRepository,
            DomainEventPublisher eventPublisher,
            ActivityLogRepository activityLogRepository) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Saves the task aggregate, publishes its domain events to the outbox, and saves
     * the activity log (if non-null) — all within one transaction. Domain events are
     * pulled BEFORE the save because {@link TaskPersistenceAdapter} reloads the aggregate
     * from the database, which would discard the in-memory event list on the original instance.
     *
     * @param task        the aggregate to persist
     * @param activityLog the activity log entry to save alongside (nullable)
     * @return the reloaded, persisted task
     */
    @Override
    @Transactional
    public Task saveAndPublish(Task task, ActivityLog activityLog) {
        // Pull events before save — TaskPersistenceAdapter.save() re-fetches from DB,
        // which would discard the in-memory event list on the original aggregate instance.
        List<Object> events = task.pullDomainEvents();
        Task saved = taskRepository.save(task);
        eventPublisher.publish(events);
        if (activityLog != null) {
            activityLogRepository.save(activityLog);
        }
        return saved;
    }

    /**
     * Atomically bulk-deletes all subtasks of the root task, publishes the root's pending
     * domain events to the outbox, and deletes the root task row — ALL in one transaction.
     *
     * <p>Operation order:
     * <ol>
     *   <li>Pull domain events from the aggregate (before any deletes, so the in-memory
     *       event list is captured before the aggregate is gone).</li>
     *   <li>Bulk-delete subtasks ({@link TaskRepository#bulkDeleteSubtasks}) — single DELETE.</li>
     *   <li>Publish events to the outbox ({@link DomainEventPublisher#publish}).</li>
     *   <li>Delete the root task row ({@link TaskRepository#deleteByIdAndTenantId}).</li>
     * </ol>
     *
     * <p>All four steps participate in the SAME {@code @Transactional} boundary (REQUIRED
     * propagation on the repository methods joins this transaction). A failure in any step
     * rolls back all, preventing split-brain where subtasks are deleted but the root still
     * exists or no event was published.
     *
     * @param root the root task aggregate with pending events raised by
     *             {@link Task#markDeleted()}; its subtasks and row will be deleted
     */
    @Override
    @Transactional
    public void publishAndDeleteWithSubtasks(Task root) {
        // Pull events first — must happen before deletion so event data is available.
        List<Object> events = root.pullDomainEvents();

        // Bulk-delete all children in one DELETE (no N+1).
        taskRepository.bulkDeleteSubtasks(root.getId(), root.getTenantId());

        // Publish TaskDeleted (and any other events) to the outbox within this transaction.
        eventPublisher.publish(events);

        // Finally, delete the root task row.
        taskRepository.deleteByIdAndTenantId(root.getId(), root.getTenantId());
    }
}
