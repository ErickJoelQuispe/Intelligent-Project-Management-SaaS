package com.epm.user.domain.port.in.command;

import java.util.UUID;

/**
 * Command to update a team's metadata.
 */
public record UpdateTeamCommand(
        UUID teamId,
        UUID requesterId,
        UUID tenantId,
        String name,
        String description) {
}
