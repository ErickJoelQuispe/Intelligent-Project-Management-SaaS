package com.epm.task.infrastructure.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.result.TaskResult;

/**
 * Response DTO for task operations.
 */
public record TaskResponse(
        UUID id,
        UUID tenantId,
        UUID projectId,
        UUID parentTaskId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate deadline,
        UUID assigneeId,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskResponse from(TaskResult result) {
        return new TaskResponse(
                result.id(),
                result.tenantId(),
                result.projectId(),
                result.parentTaskId(),
                result.title(),
                result.description(),
                result.status(),
                result.priority(),
                result.deadline(),
                result.assigneeId(),
                result.createdAt(),
                result.updatedAt());
    }
}
