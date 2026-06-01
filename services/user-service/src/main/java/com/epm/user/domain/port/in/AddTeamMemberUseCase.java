package com.epm.user.domain.port.in;

import java.util.UUID;

import com.epm.user.domain.port.in.command.AddMemberCommand;

/**
 * Driving port: adds a member to a team.
 */
public interface AddTeamMemberUseCase {

    /**
     * Adds a member to the specified team.
     *
     * @param teamId   the team to add a member to
     * @param callerId the authenticated user (must be OWNER)
     * @param tenantId the authenticated user's tenant
     * @param command  the add member command
     * @throws com.epm.user.domain.exception.UnauthorizedException  if caller is not OWNER
     * @throws com.epm.user.domain.exception.UserNotFoundException   if target user not found
     * @throws com.epm.user.domain.exception.DuplicateMemberException if already a member
     */
    void addMember(UUID teamId, UUID callerId, UUID tenantId, AddMemberCommand command);
}
