package com.epm.task.application.usecase;

import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.UpdateTaskUseCase;
import com.epm.task.domain.port.in.command.UpdateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;

/**
 * Implementation of {@link UpdateTaskUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class UpdateTaskUseCaseImpl implements UpdateTaskUseCase {

    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final DomainEventPublisher eventPublisher;

    public UpdateTaskUseCaseImpl(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.activityLogRepository = activityLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TaskResult execute(UpdateTaskCommand command) {
        Task task = taskRepository.findByIdAndTenantId(command.taskId(), command.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(command.taskId(), command.tenantId()));

        task.update(command.title(), command.description(), command.priority(), command.deadline());
        Task saved = taskRepository.save(task);
        eventPublisher.publish(saved.pullDomainEvents());

        ActivityLog log = ActivityLog.create(
                saved.getId(), command.tenantId(), "UPDATED", command.callerId());
        activityLogRepository.save(log);

        return TaskMapper.toResult(saved);
    }
}
