package com.epm.task.application.usecase;

import com.epm.task.domain.exception.ProjectMembershipRequiredException;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.AssignTaskUseCase;
import com.epm.task.domain.port.in.command.AssignTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link AssignTaskUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Membership check (Feign HTTP call, cached up to 30 s) is performed BEFORE the write
 * transaction so the DB connection is not held during the network round-trip.
 */
public class AssignTaskUseCaseImpl implements AssignTaskUseCase {

    private final TaskRepository taskRepository;
    private final TransactionalOutboxWriter outboxWriter;
    private final ProjectMembershipPort membershipPort;

    public AssignTaskUseCaseImpl(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort) {
        this.taskRepository = taskRepository;
        this.outboxWriter = outboxWriter;
        this.membershipPort = membershipPort;
    }

    @Override
    public TaskResult execute(AssignTaskCommand command) {
        // Load first to obtain projectId for the membership check
        Task task = taskRepository.findByIdAndTenantId(command.taskId(), command.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(command.taskId(), command.tenantId()));

        // Membership check BEFORE the write transaction (Feign call outside @Transactional)
        boolean isMember = membershipPort.isMember(task.getProjectId(), command.callerId(), command.tenantId());
        if (!isMember) {
            throw new ProjectMembershipRequiredException(command.callerId(), task.getProjectId());
        }

        task.assign(command.assigneeId());

        ActivityLog log = ActivityLog.create(task.getId(), command.tenantId(), "ASSIGNED", command.callerId());
        Task saved = outboxWriter.saveAndPublish(task, log);

        return TaskMapper.toResult(saved);
    }
}
