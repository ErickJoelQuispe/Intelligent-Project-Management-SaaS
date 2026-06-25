package com.epm.project.domain.port.in;

import java.util.UUID;

import com.epm.project.domain.port.in.result.ProjectResult;

/**
 * Driving port: restores an archived project back to ACTIVE status.
 */
public interface RestoreProjectUseCase {

    ProjectResult execute(UUID projectId, UUID callerProfileId, UUID tenantId);
}
