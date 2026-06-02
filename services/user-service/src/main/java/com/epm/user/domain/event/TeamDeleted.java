package com.epm.user.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a team is deleted.
 */
public record TeamDeleted(
        UUID eventId,
        UUID teamId,
        UUID tenantId,
        Instant occurredAt) {
}
