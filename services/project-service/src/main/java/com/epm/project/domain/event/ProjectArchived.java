package com.epm.project.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a project is archived.
 */
public record ProjectArchived(
        UUID eventId,
        UUID projectId,
        UUID tenantId,
        Instant occurredAt) {
}
