package com.epm.task.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a task is created.
 */
public record TaskCreated(
        UUID eventId,
        UUID taskId,
        UUID projectId,
        String title,
        UUID tenantId,
        Instant occurredAt) {
}
