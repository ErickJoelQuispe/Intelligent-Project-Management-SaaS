package com.epm.project.domain.port.in.command;

import java.util.UUID;

import com.epm.project.domain.model.ProjectVisibility;

/**
 * Command to create a new project.
 */
public record CreateProjectCommand(
        String name,
        String description,
        ProjectVisibility visibility,
        UUID callerProfileId,
        UUID tenantId) {
}
