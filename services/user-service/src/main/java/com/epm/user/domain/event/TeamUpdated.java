package com.epm.user.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a team's metadata is updated.
 */
public record TeamUpdated(
        UUID eventId,
        UUID teamId,
        UUID tenantId,
        String name,
        Instant occurredAt) {
}
