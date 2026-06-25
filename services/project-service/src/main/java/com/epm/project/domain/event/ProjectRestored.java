package com.epm.project.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a project is restored (unarchived).
 */
public record ProjectRestored(
        UUID eventId,
        UUID projectId,
        String name,
        UUID ownerId,
        UUID tenantId,
        Instant occurredAt) {
}
