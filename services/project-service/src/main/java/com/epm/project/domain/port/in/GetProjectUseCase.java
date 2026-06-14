package com.epm.project.domain.port.in;

import java.util.Set;
import java.util.UUID;

import com.epm.project.domain.port.in.result.ProjectResult;

/**
 * Driving port: retrieves a single project by ID.
 *
 * <p>Access control applies visibility semantics: PUBLIC is open; PRIVATE requires
 * direct membership; TEAM requires membership OR belonging to an assigned team
 * (provided via {@code callerTeamIds}).
 */
public interface GetProjectUseCase {

    /**
     * Retrieves a project the caller is authorized to see.
     *
     * @param projectId       the project to retrieve
     * @param callerProfileId the profile requesting access
     * @param tenantId        the caller's tenant
     * @param callerTeamIds   the set of teams the caller belongs to; pass empty set
     *                        if team claims are not yet available in the JWT
     * @return the project result if the caller is authorized
     * @throws com.epm.project.domain.exception.ProjectNotFoundException            if not found
     * @throws com.epm.project.domain.exception.UnauthorizedProjectAccessException  if not authorized
     */
    ProjectResult execute(UUID projectId, UUID callerProfileId, UUID tenantId,
            Set<UUID> callerTeamIds);
}
