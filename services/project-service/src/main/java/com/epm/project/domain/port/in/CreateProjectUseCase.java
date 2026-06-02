package com.epm.project.domain.port.in;

import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.epm.project.domain.port.in.result.ProjectResult;

/**
 * Driving port: creates a new project.
 */
public interface CreateProjectUseCase {

    ProjectResult execute(CreateProjectCommand command);
}
