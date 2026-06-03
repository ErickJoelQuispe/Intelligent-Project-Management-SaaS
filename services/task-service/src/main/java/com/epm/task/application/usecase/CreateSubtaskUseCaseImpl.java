package com.epm.task.application.usecase;

import com.epm.task.domain.exception.MaxDepthExceededException;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.CreateSubtaskUseCase;
import com.epm.task.domain.port.in.command.CreateSubtaskCommand;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;

/**
 * Implementation of {@link CreateSubtaskUseCase}.
 *
 * <p>Enforces the 2-level depth limit: a subtask (parentTaskId != null) cannot
 * have children.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class CreateSubtaskUseCaseImpl implements CreateSubtaskUseCase {

    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final DomainEventPublisher eventPublisher;

    public CreateSubtaskUseCaseImpl(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.activityLogRepository = activityLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TaskResult execute(CreateSubtaskCommand command) {
        Task parent = taskRepository.findByIdAndTenantId(command.parentTaskId(), command.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(command.parentTaskId(), command.tenantId()));

        if (parent.getParentTaskId() != null) {
            throw new MaxDepthExceededException(command.parentTaskId());
        }

        CreateTaskCommand createCommand = new CreateTaskCommand(
                command.tenantId(),
                command.projectId(),
                command.callerId(),
                command.title(),
                command.description(),
                command.priority(),
                command.deadline(),
                command.assigneeId());

        Task subtask = Task.createSubtask(createCommand, command.parentTaskId());
        Task saved = taskRepository.save(subtask);
        eventPublisher.publish(saved.pullDomainEvents());

        ActivityLog log = ActivityLog.create(
                saved.getId(), command.tenantId(), "CREATED", command.callerId());
        activityLogRepository.save(log);

        return TaskMapper.toResult(saved);
    }
}
