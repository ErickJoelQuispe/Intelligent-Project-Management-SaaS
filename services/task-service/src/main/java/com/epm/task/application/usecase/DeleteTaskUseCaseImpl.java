package com.epm.task.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.DeleteTaskUseCase;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;

/**
 * Implementation of {@link DeleteTaskUseCase}.
 *
 * <p>Cascade-cancels subtasks before deleting the root task.
 * Publishes a {@link com.epm.task.domain.event.TaskDeleted} event for the root
 * and {@link com.epm.task.domain.event.TaskStatusChanged} events for each subtask.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class DeleteTaskUseCaseImpl implements DeleteTaskUseCase {

    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final DomainEventPublisher eventPublisher;

    public DeleteTaskUseCaseImpl(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.activityLogRepository = activityLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void execute(UUID taskId, UUID tenantId) {
        Task root = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        List<Task> subtasks = taskRepository.findSubtasksByParentId(taskId, tenantId);
        for (Task subtask : subtasks) {
            subtask.cancel();
            taskRepository.save(subtask);
            eventPublisher.publish(subtask.pullDomainEvents());

            ActivityLog log = ActivityLog.create(
                    subtask.getId(), tenantId, "CANCELLED", taskId);
            activityLogRepository.save(log);
        }

        root.markDeleted();
        eventPublisher.publish(root.pullDomainEvents());
        taskRepository.deleteByIdAndTenantId(taskId, tenantId);
    }
}
