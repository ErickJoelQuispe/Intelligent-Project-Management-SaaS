package com.epm.user.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.epm.user.domain.model.TeamRole;

/**
 * Domain event emitted when a member joins a team.
 */
public record TeamMemberJoined(
        UUID eventId,
        UUID teamId,
        UUID tenantId,
        UUID userId,
        TeamRole role,
        Instant occurredAt) {
}
