package com.epm.task.application.usecase;

import java.util.List;

import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.AssignTaskUseCase;
import com.epm.task.domain.port.in.command.AssignTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.TaskRepository;

/**
 * Implementation of {@link AssignTaskUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class AssignTaskUseCaseImpl implements AssignTaskUseCase {

    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final DomainEventPublisher eventPublisher;

    public AssignTaskUseCaseImpl(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.activityLogRepository = activityLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TaskResult execute(AssignTaskCommand command) {
        Task task = taskRepository.findByIdAndTenantId(command.taskId(), command.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(command.taskId(), command.tenantId()));

        task.assign(command.assigneeId());
        List<Object> events = task.pullDomainEvents();
        Task saved = taskRepository.save(task);
        eventPublisher.publish(events);

        ActivityLog log = ActivityLog.create(
                saved.getId(), command.tenantId(), "ASSIGNED", command.callerId());
        activityLogRepository.save(log);

        return TaskMapper.toResult(saved);
    }
}
