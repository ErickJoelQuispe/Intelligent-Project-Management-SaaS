package com.epm.user.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a member leaves (is removed from) a team.
 */
public record TeamMemberLeft(
        UUID eventId,
        UUID teamId,
        UUID tenantId,
        UUID userId,
        String teamName,
        Instant occurredAt) {
}
