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
     * Publishes the task's pending domain events to the outbox and deletes the task row —
     * all within one transaction. Used in the delete flow where the aggregate must be
     * removed rather than updated.
     *
     * <p>Events are pulled before deletion to ensure they are inserted into the outbox
     * within the same transaction as the DELETE.
     *
     * @param task the aggregate whose events are to be published before deletion
     */
    @Override
    @Transactional
    public void publishAndDelete(Task task) {
        List<Object> events = task.pullDomainEvents();
        eventPublisher.publish(events);
        taskRepository.deleteByIdAndTenantId(task.getId(), task.getTenantId());
    }
}
