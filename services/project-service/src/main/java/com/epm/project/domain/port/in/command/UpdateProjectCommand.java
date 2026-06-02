package com.epm.project.domain.port.in.command;

import java.util.UUID;

import com.epm.project.domain.model.ProjectVisibility;

/**
 * Command to update an existing project.
 */
public record UpdateProjectCommand(
        UUID projectId,
        String name,
        String description,
        ProjectVisibility visibility,
        UUID callerProfileId,
        UUID tenantId) {
}
