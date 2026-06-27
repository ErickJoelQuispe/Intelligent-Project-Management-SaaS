package com.epm.user.domain.port.in;

import com.epm.user.domain.port.in.command.UpdateTeamMemberRoleCommand;
import com.epm.user.domain.port.in.result.TeamResult;

/**
 * Driving port: changes a team member's role.
 */
public interface UpdateTeamMemberRoleUseCase {

    /**
     * Changes the role of the specified member.
     *
     * @param command the role-change command
     * @return the updated team result
     * @throws com.epm.user.domain.exception.TeamNotFoundException  if team or member not found
     * @throws com.epm.user.domain.exception.UnauthorizedException  if caller is not the owner
     * @throws com.epm.user.domain.exception.SelfRoleChangeException if owner tries to change own role
     */
    TeamResult execute(UpdateTeamMemberRoleCommand command);
}
