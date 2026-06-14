package com.epm.task.application.usecase;

import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.UpdateTaskUseCase;
import com.epm.task.domain.port.in.command.UpdateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link UpdateTaskUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class UpdateTaskUseCaseImpl implements UpdateTaskUseCase {

    private final TaskRepository taskRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public UpdateTaskUseCaseImpl(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.taskRepository = taskRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public TaskResult execute(UpdateTaskCommand command) {
        Task task = taskRepository.findByIdAndTenantId(command.taskId(), command.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(command.taskId(), command.tenantId()));

        task.update(command.title(), command.description(), command.priority(), command.deadline());

        ActivityLog log = ActivityLog.create(task.getId(), command.tenantId(), "UPDATED", command.callerId());
        Task saved = outboxWriter.saveAndPublish(task, log);

        return TaskMapper.toResult(saved);
    }
}
