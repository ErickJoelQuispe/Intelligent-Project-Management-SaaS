package com.epm.task.application.usecase;

import java.util.List;

import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.ChangeTaskStatusUseCase;
import com.epm.task.domain.port.in.command.ChangeStatusCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;

/**
 * Implementation of {@link ChangeTaskStatusUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class ChangeTaskStatusUseCaseImpl implements ChangeTaskStatusUseCase {

    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final DomainEventPublisher eventPublisher;

    public ChangeTaskStatusUseCaseImpl(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.activityLogRepository = activityLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TaskResult execute(ChangeStatusCommand command) {
        Task task = taskRepository.findByIdAndTenantId(command.taskId(), command.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(command.taskId(), command.tenantId()));

        task.changeStatus(command.newStatus());
        List<Object> events = task.pullDomainEvents();
        Task saved = taskRepository.save(task);
        eventPublisher.publish(events);

        ActivityLog log = ActivityLog.create(
                saved.getId(), command.tenantId(), "STATUS_CHANGED", command.callerId());
        activityLogRepository.save(log);

        return TaskMapper.toResult(saved);
    }
}
