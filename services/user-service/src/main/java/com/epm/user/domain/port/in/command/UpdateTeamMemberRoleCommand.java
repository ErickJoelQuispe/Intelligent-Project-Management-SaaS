package com.epm.user.domain.port.in.command;

import java.util.UUID;

import com.epm.user.domain.model.TeamRole;

/**
 * Command to change a team member's role.
 */
public record UpdateTeamMemberRoleCommand(
        UUID teamId,
        UUID memberId,
        UUID requesterId,
        UUID tenantId,
        TeamRole newRole) {
}
