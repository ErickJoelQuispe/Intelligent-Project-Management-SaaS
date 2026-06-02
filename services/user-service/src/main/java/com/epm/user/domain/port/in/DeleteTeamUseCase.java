package com.epm.user.domain.port.in;

import java.util.UUID;

/**
 * Driving port: deletes a team.
 */
public interface DeleteTeamUseCase {

    /**
     * Deletes the team identified by {@code teamId}.
     *
     * @param teamId   the team to delete
     * @param callerId the authenticated user (must be OWNER)
     * @param tenantId the authenticated user's tenant
     * @throws com.epm.user.domain.exception.TeamNotFoundException  if the team does not exist
     * @throws com.epm.user.domain.exception.UnauthorizedException  if caller is not OWNER
     */
    void deleteTeam(UUID teamId, UUID callerId, UUID tenantId);
}
