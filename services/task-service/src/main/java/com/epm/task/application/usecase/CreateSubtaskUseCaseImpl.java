package com.epm.task.application.usecase;

import com.epm.task.domain.exception.MaxDepthExceededException;
import com.epm.task.domain.exception.ProjectMembershipRequiredException;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.CreateSubtaskUseCase;
import com.epm.task.domain.port.in.command.CreateSubtaskCommand;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link CreateSubtaskUseCase}.
 *
 * <p>Enforces the 2-level depth limit: a subtask (parentTaskId != null) cannot
 * have children.
 *
 * <p>Membership check (Feign HTTP call, cached up to 30 s) is performed BEFORE the write
 * transaction so the DB connection is not held during the network round-trip.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class CreateSubtaskUseCaseImpl implements CreateSubtaskUseCase {

    private final TaskRepository taskRepository;
    private final TransactionalOutboxWriter outboxWriter;
    private final ProjectMembershipPort membershipPort;

    public CreateSubtaskUseCaseImpl(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort) {
        this.taskRepository = taskRepository;
        this.outboxWriter = outboxWriter;
        this.membershipPort = membershipPort;
    }

    @Override
    public TaskResult execute(CreateSubtaskCommand command) {
        Task parent = taskRepository.findByIdAndTenantId(command.parentTaskId(), command.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(command.parentTaskId(), command.tenantId()));

        if (parent.getParentTaskId() != null) {
            throw new MaxDepthExceededException(command.parentTaskId());
        }

        // Membership check BEFORE the write transaction (Feign call outside @Transactional)
        boolean isMember = membershipPort.isMember(command.projectId(), command.callerId(), command.tenantId());
        if (!isMember) {
            throw new ProjectMembershipRequiredException(command.callerId(), command.projectId());
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

        ActivityLog log = ActivityLog.create(subtask.getId(), command.tenantId(), "CREATED", command.callerId());
        Task saved = outboxWriter.saveAndPublish(subtask, log);

        return TaskMapper.toResult(saved);
    }
}
