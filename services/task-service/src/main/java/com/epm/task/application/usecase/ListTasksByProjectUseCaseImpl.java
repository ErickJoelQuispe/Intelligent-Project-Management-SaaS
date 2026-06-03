package com.epm.task.application.usecase;

import java.util.UUID;

import com.epm.task.domain.port.in.ListTasksByProjectUseCase;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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
    public Page<TaskResult> execute(UUID projectId, UUID tenantId, int page, int size) {
        return taskRepository
                .findAllByProjectIdAndTenantId(projectId, tenantId, PageRequest.of(page, size))
                .map(TaskMapper::toResult);
    }
}
