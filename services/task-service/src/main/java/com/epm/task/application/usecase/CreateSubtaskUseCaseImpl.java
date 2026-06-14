package com.epm.task.application.usecase;

import com.epm.task.domain.exception.MaxDepthExceededException;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.CreateSubtaskUseCase;
import com.epm.task.domain.port.in.command.CreateSubtaskCommand;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;

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
    private final TransactionalOutboxWriter outboxWriter;

    public CreateSubtaskUseCaseImpl(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.taskRepository = taskRepository;
        this.outboxWriter = outboxWriter;
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

        ActivityLog log = ActivityLog.create(subtask.getId(), command.tenantId(), "CREATED", command.callerId());
        Task saved = outboxWriter.saveAndPublish(subtask, log);

        return TaskMapper.toResult(saved);
    }
}
