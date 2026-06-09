package com.epm.task.application.usecase;

import java.util.UUID;

import com.epm.task.domain.model.PageResult;
import com.epm.task.domain.port.in.ListTasksByProjectUseCase;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.TaskRepository;

/**
 * Implementation of {@link ListTasksByProjectUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class ListTasksByProjectUseCaseImpl implements ListTasksByProjectUseCase {

    private final TaskRepository taskRepository;

    public ListTasksByProjectUseCaseImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public PageResult<TaskResult> execute(UUID projectId, UUID tenantId, int page, int size) {
        return taskRepository
                .findAllByProjectIdAndTenantId(projectId, tenantId, page, size)
                .map(TaskMapper::toResult);
    }
}
