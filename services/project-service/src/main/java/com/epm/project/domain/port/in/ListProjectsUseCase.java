package com.epm.project.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.epm.project.domain.port.in.result.ProjectResult;

/**
 * Driving port: lists projects accessible to the caller.
 */
public interface ListProjectsUseCase {

    List<ProjectResult> execute(UUID callerProfileId, UUID tenantId);

    /**
     * Lists projects accessible to the caller, optionally including archived ones.
     *
     * @param callerProfileId the profile making the request
     * @param tenantId        the caller's tenant
     * @param includeArchived when {@code true}, archived projects are included in the result
     * @return list of matching projects
     */
    List<ProjectResult> execute(UUID callerProfileId, UUID tenantId, boolean includeArchived);
}
