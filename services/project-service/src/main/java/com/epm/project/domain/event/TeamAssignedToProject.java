package com.epm.project.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a team is assigned to a project.
 */
public record TeamAssignedToProject(
        UUID eventId,
        UUID projectId,
        UUID teamId,
        UUID tenantId,
        Instant occurredAt) {
}
