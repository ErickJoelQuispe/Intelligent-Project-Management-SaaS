package com.epm.task.application.usecase;

import com.epm.task.domain.exception.ProjectMembershipRequiredException;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.UpdateTaskUseCase;
import com.epm.task.domain.port.in.command.UpdateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link UpdateTaskUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Membership check (Feign HTTP call, cached up to 30 s) is performed BEFORE the write
 * transaction so the DB connection is not held during the network round-trip.
 * {@link TransactionalOutboxWriter#saveAndPublish} is the sole transactional boundary.
 */
public class UpdateTaskUseCaseImpl implements UpdateTaskUseCase {

    private final TaskRepository taskRepository;
    private final TransactionalOutboxWriter outboxWriter;
    private final ProjectMembershipPort membershipPort;

    public UpdateTaskUseCaseImpl(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort) {
        this.taskRepository = taskRepository;
        this.outboxWriter = outboxWriter;
        this.membershipPort = membershipPort;
    }

    @Override
    public TaskResult execute(UpdateTaskCommand command) {
        // Load first to obtain projectId for the membership check
        Task task = taskRepository.findByIdAndTenantId(command.taskId(), command.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(command.taskId(), command.tenantId()));

        // Membership check BEFORE the write transaction (Feign call outside @Transactional)
        boolean isMember = membershipPort.isMember(task.getProjectId(), command.callerId(), command.tenantId());
        if (!isMember) {
            throw new ProjectMembershipRequiredException(command.callerId(), task.getProjectId());
        }

        task.update(command.title(), command.description(), command.priority(), command.deadline());

        ActivityLog log = ActivityLog.create(task.getId(), command.tenantId(), "UPDATED", command.callerId());
        Task saved = outboxWriter.saveAndPublish(task, log);

        return TaskMapper.toResult(saved);
    }
}
