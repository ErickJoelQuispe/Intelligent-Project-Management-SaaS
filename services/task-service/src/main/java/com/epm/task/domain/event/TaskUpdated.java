package com.epm.task.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a task's fields are updated.
 */
public record TaskUpdated(
        UUID eventId,
        UUID taskId,
        UUID tenantId,
        Instant occurredAt) {
}
