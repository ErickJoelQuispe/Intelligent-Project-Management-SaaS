package com.epm.task.application.usecase;

import java.util.List;

import com.epm.task.domain.exception.ProjectMembershipRequiredException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.CreateTaskUseCase;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ActivityLogRepository;
import com.epm.task.domain.port.out.DomainEventPublisher;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TaskRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Implementation of {@link CreateTaskUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class CreateTaskUseCaseImpl implements CreateTaskUseCase {

    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final DomainEventPublisher eventPublisher;
    private final ProjectMembershipPort membershipPort;
    private final MeterRegistry meterRegistry;

    public CreateTaskUseCaseImpl(TaskRepository taskRepository,
            ActivityLogRepository activityLogRepository,
            DomainEventPublisher eventPublisher,
            ProjectMembershipPort membershipPort,
            MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.activityLogRepository = activityLogRepository;
        this.eventPublisher = eventPublisher;
        this.membershipPort = membershipPort;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public TaskResult execute(CreateTaskCommand command) {
        boolean isMember = membershipPort.isMember(command.projectId(), command.callerId(), command.tenantId());
        if (!isMember) {
            throw new ProjectMembershipRequiredException(command.callerId(), command.projectId());
        }

        Task task = Task.create(command);
        List<Object> events = task.pullDomainEvents();
        Task saved = taskRepository.save(task);
        eventPublisher.publish(events);

        ActivityLog log = ActivityLog.create(saved.getId(), command.tenantId(), "CREATED", command.callerId());
        activityLogRepository.save(log);

        Counter.builder("tasks.created")
                .tag("tenantId", command.tenantId().toString())
                .register(meterRegistry)
                .increment();

        return TaskMapper.toResult(saved);
    }
}
