package com.epm.task.domain.port.in.command;

import java.time.LocalDate;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;

/**
 * Command record for creating a new root task.
 */
public record CreateTaskCommand(
        UUID tenantId,
        UUID projectId,
        UUID callerId,
        String title,
        String description,
        TaskPriority priority,
        LocalDate deadline,
        UUID assigneeId) {
}
