package com.epm.user.domain.port.in;

import java.util.UUID;

import com.epm.user.domain.port.in.result.TeamResult;

/**
 * Driving port: retrieves a team by ID.
 */
public interface GetTeamUseCase {

    /**
     * Returns the team with the given ID if the caller is a member.
     * Returns the same exception whether not found OR caller not a member (no info leakage).
     *
     * @param teamId   the team's ID
     * @param userId   the authenticated user's ID
     * @param tenantId the authenticated user's tenant
     * @return the team result
     * @throws com.epm.user.domain.exception.TeamNotFoundException if not found or not a member
     */
    TeamResult getTeam(UUID teamId, UUID userId, UUID tenantId);
}
