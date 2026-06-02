package com.epm.project.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a project is created.
 */
public record ProjectCreated(
        UUID eventId,
        UUID projectId,
        String name,
        UUID ownerId,
        UUID tenantId,
        Instant occurredAt) {
}
