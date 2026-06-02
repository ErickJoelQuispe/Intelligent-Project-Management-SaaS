package com.epm.project.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a project is updated.
 */
public record ProjectUpdated(
        UUID eventId,
        UUID projectId,
        String name,
        String description,
        UUID tenantId,
        Instant occurredAt) {
}
