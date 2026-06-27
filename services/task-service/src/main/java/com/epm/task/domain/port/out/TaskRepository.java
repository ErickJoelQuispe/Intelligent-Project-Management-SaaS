package com.epm.task.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.task.domain.model.PageResult;
import com.epm.task.domain.model.Task;

/**
 * Driven port for task persistence.
 */
public interface TaskRepository {

    Task save(Task task);

    Optional<Task> findByIdAndTenantId(UUID id, UUID tenantId);

    PageResult<Task> findAllByProjectIdAndTenantId(UUID projectId, UUID tenantId, int page, int size);

    List<Task> findAllByProjectId(UUID projectId, UUID tenantId);

    List<Task> findSubtasksByParentId(UUID parentTaskId, UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Bulk-cancels all non-cancelled tasks in the given project (for cascade on archive).
     * Returns the count of rows updated.
     *
     * <p>This is a single bulk UPDATE — no N+1. Does NOT emit per-task domain events;
     * callers should emit one aggregate {@code ProjectTasksCancelled} event instead.
     */
    int bulkCancelByProjectId(UUID projectId, UUID tenantId);

    /**
     * Bulk-deletes all subtasks of the given parent task.
     *
     * <p>This is a single bulk DELETE — no N+1. Child deletion is implied by the
     * root {@code TaskDeleted} event; no per-child events are emitted.
     */
    void bulkDeleteSubtasks(UUID parentTaskId, UUID tenantId);

    /**
     * Deletes all activity log entries for the given task.
     * Must be called before deleting the task row to avoid FK violations.
     */
    void deleteActivityLogsByTaskId(UUID taskId);
}
