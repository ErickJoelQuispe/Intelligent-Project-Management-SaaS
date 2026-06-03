package com.epm.task.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a task is assigned to a user.
 */
public record TaskAssigned(
        UUID eventId,
        UUID taskId,
        UUID assigneeId,
        UUID tenantId,
        Instant occurredAt) {
}
