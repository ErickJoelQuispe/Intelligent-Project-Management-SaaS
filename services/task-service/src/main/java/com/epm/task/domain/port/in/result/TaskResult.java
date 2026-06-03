package com.epm.task.domain.port.in.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;

/**
 * Read model returned by task use cases.
 */
public record TaskResult(
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
}
