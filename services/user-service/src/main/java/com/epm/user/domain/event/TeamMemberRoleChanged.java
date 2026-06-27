package com.epm.user.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.epm.user.domain.model.TeamRole;

/**
 * Domain event emitted when a team member's role is changed.
 */
public record TeamMemberRoleChanged(
        UUID eventId,
        UUID teamId,
        UUID tenantId,
        UUID userId,
        TeamRole oldRole,
        TeamRole newRole,
        Instant occurredAt) {
}
