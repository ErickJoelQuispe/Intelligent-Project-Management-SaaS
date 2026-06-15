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
 *
 * <p><strong>DoS guard</strong>: page and size are clamped before the DB query.
 * page is clamped to {@code [0, +∞)}, size to {@code [1, MAX_PAGE_SIZE]}.
 * Invalid values are silently clamped rather than rejected; the controller layer
 * additionally validates the raw params via {@code @Validated} + {@code @Min}.
 */
public class ListTasksByProjectUseCaseImpl implements ListTasksByProjectUseCase {

    static final int MAX_PAGE_SIZE = 100;

    private final TaskRepository taskRepository;

    public ListTasksByProjectUseCaseImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public PageResult<TaskResult> execute(UUID projectId, UUID tenantId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return taskRepository
                .findAllByProjectIdAndTenantId(projectId, tenantId, safePage, safeSize)
                .map(TaskMapper::toResult);
    }
}
