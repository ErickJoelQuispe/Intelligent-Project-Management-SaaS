package com.epm.task.domain.port.in;

import java.util.UUID;

import com.epm.task.domain.port.in.result.TaskResult;
import org.springframework.data.domain.Page;

/**
 * Driving port: lists tasks for a project with pagination.
 */
public interface ListTasksByProjectUseCase {

    Page<TaskResult> execute(UUID projectId, UUID tenantId, int page, int size);
}
