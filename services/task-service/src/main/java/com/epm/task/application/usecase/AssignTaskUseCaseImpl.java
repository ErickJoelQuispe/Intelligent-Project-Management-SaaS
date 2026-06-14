package com.epm.task.application.usecase;

import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.AssignTaskUseCase;
import com.epm.task.domain.port.in.command.AssignTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link AssignTaskUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class AssignTaskUseCaseImpl implements AssignTaskUseCase {

    private final TaskRepository taskRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public AssignTaskUseCaseImpl(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.taskRepository = taskRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public TaskResult execute(AssignTaskCommand command) {
        Task task = taskRepository.findByIdAndTenantId(command.taskId(), command.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(command.taskId(), command.tenantId()));

        task.assign(command.assigneeId());

        ActivityLog log = ActivityLog.create(task.getId(), command.tenantId(), "ASSIGNED", command.callerId());
        Task saved = outboxWriter.saveAndPublish(task, log);

        return TaskMapper.toResult(saved);
    }
}
