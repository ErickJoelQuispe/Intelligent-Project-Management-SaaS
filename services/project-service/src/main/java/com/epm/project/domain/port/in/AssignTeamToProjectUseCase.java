package com.epm.project.domain.port.in;

import com.epm.project.domain.port.in.command.AssignTeamCommand;

/**
 * Driving port: assigns a team to a project.
 */
public interface AssignTeamToProjectUseCase {

    void execute(AssignTeamCommand command);
}
