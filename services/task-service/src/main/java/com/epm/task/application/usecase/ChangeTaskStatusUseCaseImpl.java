package com.epm.task.application.usecase;

import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.ChangeTaskStatusUseCase;
import com.epm.task.domain.port.in.command.ChangeStatusCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link ChangeTaskStatusUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Status transitions are FSM-validated in {@link Task#changeStatus}: illegal transitions
 * throw {@link com.epm.task.domain.exception.InvalidStatusException}; same-state transitions
 * are a no-op (no event emitted, no write to DB).
 */
public class ChangeTaskStatusUseCaseImpl implements ChangeTaskStatusUseCase {

    private final TaskRepository taskRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public ChangeTaskStatusUseCaseImpl(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.taskRepository = taskRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public TaskResult execute(ChangeStatusCommand command) {
        Task task = taskRepository.findByIdAndTenantId(command.taskId(), command.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(command.taskId(), command.tenantId()));

        task.changeStatus(command.newStatus());

        // Same-state no-op: if changeStatus() emitted no event there is nothing to save
        if (task.getDomainEvents().isEmpty()) {
            return TaskMapper.toResult(task);
        }

        ActivityLog log = ActivityLog.create(task.getId(), command.tenantId(), "STATUS_CHANGED", command.callerId());
        Task saved = outboxWriter.saveAndPublish(task, log);

        return TaskMapper.toResult(saved);
    }
}
