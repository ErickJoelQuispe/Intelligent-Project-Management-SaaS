package com.epm.user.domain.port.in.command;

import java.util.UUID;

import com.epm.user.domain.model.TeamRole;

/**
 * Command to add a member to a team.
 */
public record AddMemberCommand(UUID userId, TeamRole role) {
}
