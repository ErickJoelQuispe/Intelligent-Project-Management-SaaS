package com.epm.task.domain.port.in;

import java.util.UUID;

import com.epm.task.domain.model.PageResult;
import com.epm.task.domain.port.in.result.TaskResult;

/**
 * Driving port: lists tasks for a project with pagination.
 */
public interface ListTasksByProjectUseCase {

    PageResult<TaskResult> execute(UUID projectId, UUID tenantId, int page, int size);
}
