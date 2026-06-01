package com.epm.user.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.port.in.result.TeamResult;

/**
 * Driving port: lists teams the calling user belongs to.
 */
public interface ListTeamsUseCase {

    /**
     * Returns all teams where the user has an active membership.
     * Never throws — returns empty list if none.
     *
     * @param userId   the authenticated user's ID
     * @param tenantId the authenticated user's tenant
     * @return list of team results (may be empty)
     */
    List<TeamResult> listTeams(UUID userId, UUID tenantId);
}
