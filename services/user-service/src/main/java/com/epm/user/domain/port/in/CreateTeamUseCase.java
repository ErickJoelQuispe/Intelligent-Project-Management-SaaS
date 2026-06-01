package com.epm.user.domain.port.in;

import java.util.UUID;

import com.epm.user.domain.port.in.command.CreateTeamCommand;
import com.epm.user.domain.port.in.result.TeamResult;

/**
 * Driving port: creates a new team.
 */
public interface CreateTeamUseCase {

    /**
     * Creates a new team owned by the caller.
     *
     * @param ownerId  the authenticated user who becomes OWNER
     * @param tenantId the authenticated user's tenant
     * @param command  team creation data
     * @return the created team result
     */
    TeamResult createTeam(UUID ownerId, UUID tenantId, CreateTeamCommand command);
}
