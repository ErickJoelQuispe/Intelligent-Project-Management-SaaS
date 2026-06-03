package com.epm.task.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a task is deleted.
 */
public record TaskDeleted(
        UUID eventId,
        UUID taskId,
        UUID projectId,
        UUID tenantId,
        Instant occurredAt) {
}
