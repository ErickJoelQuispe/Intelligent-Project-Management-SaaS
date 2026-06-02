package com.epm.project.domain.port.in;

import com.epm.project.domain.port.in.command.UpdateProjectCommand;
import com.epm.project.domain.port.in.result.ProjectResult;

/**
 * Driving port: updates an existing project.
 */
public interface UpdateProjectUseCase {

    ProjectResult execute(UpdateProjectCommand command);
}
