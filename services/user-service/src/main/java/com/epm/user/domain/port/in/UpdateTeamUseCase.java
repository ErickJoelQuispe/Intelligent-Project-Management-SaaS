package com.epm.user.domain.port.in;

import com.epm.user.domain.port.in.command.UpdateTeamCommand;
import com.epm.user.domain.port.in.result.TeamResult;

/**
 * Driving port: updates a team's name and/or description.
 */
public interface UpdateTeamUseCase {

    /**
     * Updates the team identified by the command's teamId.
     *
     * @param command the update command
     * @return the updated team result
     * @throws com.epm.user.domain.exception.TeamNotFoundException if team not found
     * @throws com.epm.user.domain.exception.UnauthorizedException if caller is not the owner
     */
    TeamResult execute(UpdateTeamCommand command);
}
