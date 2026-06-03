package com.epm.task.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.epm.task.domain.model.TaskStatus;

/**
 * Domain event emitted when a task's status changes.
 */
public record TaskStatusChanged(
        UUID eventId,
        UUID taskId,
        TaskStatus oldStatus,
        TaskStatus newStatus,
        UUID tenantId,
        Instant occurredAt) {
}
