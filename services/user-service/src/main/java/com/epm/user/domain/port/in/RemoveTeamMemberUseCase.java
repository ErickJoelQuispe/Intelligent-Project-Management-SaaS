package com.epm.user.domain.port.in;

import java.util.UUID;

/**
 * Driving port: removes a member from a team.
 */
public interface RemoveTeamMemberUseCase {

    /**
     * Removes a member from the specified team.
     *
     * @param teamId       the team to remove a member from
     * @param callerId     the authenticated user (must be OWNER)
     * @param targetUserId the user to remove
     * @param tenantId     the authenticated user's tenant
     * @throws com.epm.user.domain.exception.UnauthorizedException if caller is not OWNER
     * @throws com.epm.user.domain.exception.LastOwnerException    if removing last owner
     */
    void removeMember(UUID teamId, UUID callerId, UUID targetUserId, UUID tenantId);
}
