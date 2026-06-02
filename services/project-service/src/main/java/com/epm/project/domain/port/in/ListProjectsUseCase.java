package com.epm.project.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.epm.project.domain.port.in.result.ProjectResult;

/**
 * Driving port: lists projects accessible to the caller.
 */
public interface ListProjectsUseCase {

    List<ProjectResult> execute(UUID callerProfileId, UUID tenantId);
}
