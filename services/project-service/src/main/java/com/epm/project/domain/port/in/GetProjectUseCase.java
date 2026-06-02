package com.epm.project.domain.port.in;

import java.util.UUID;

import com.epm.project.domain.port.in.result.ProjectResult;

/**
 * Driving port: retrieves a single project by ID.
 */
public interface GetProjectUseCase {

    ProjectResult execute(UUID projectId, UUID callerProfileId, UUID tenantId);
}
