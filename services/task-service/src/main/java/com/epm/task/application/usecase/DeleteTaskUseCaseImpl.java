package com.epm.task.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.DeleteTaskUseCase;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link DeleteTaskUseCase}.
 *
 * <p>Cascade-cancels subtasks before deleting the root task.
 * Publishes a {@link com.epm.task.domain.event.TaskDeleted} event for the root
 * and {@link com.epm.task.domain.event.TaskStatusChanged} events for each subtask.
 *
 * <p>Each subtask cancel and the root delete are each atomic via
 * {@link TransactionalOutboxWriter} — task state, outbox events, and activity log
 * are committed together.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class DeleteTaskUseCaseImpl implements DeleteTaskUseCase {

    private final TaskRepository taskRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public DeleteTaskUseCaseImpl(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.taskRepository = taskRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void execute(UUID taskId, UUID tenantId) {
        Task root = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        List<Task> subtasks = taskRepository.findSubtasksByParentId(taskId, tenantId);
        for (Task subtask : subtasks) {
            subtask.cancel();
            ActivityLog log = ActivityLog.create(subtask.getId(), tenantId, "CANCELLED", taskId);
            outboxWriter.saveAndPublish(subtask, log);
        }

        root.markDeleted();
        outboxWriter.publishAndDelete(root);
    }
}
