package com.epm.user.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a team is created.
 */
public record TeamCreated(
        UUID eventId,
        UUID teamId,
        UUID tenantId,
        UUID ownerId,
        String name,
        Instant occurredAt) {
}
