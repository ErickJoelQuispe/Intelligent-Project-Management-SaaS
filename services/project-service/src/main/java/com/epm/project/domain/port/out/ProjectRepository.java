package com.epm.project.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.project.domain.model.Project;

/**
 * Driven port for project persistence.
 */
public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Project> findAllByMemberProfileId(UUID profileId, UUID tenantId);

    /**
     * Finds all active (non-archived) projects where the given profile is a member.
     *
     * @param profileId member's profile identifier
     * @param tenantId  tenant scope
     * @return list of non-archived projects
     */
    List<Project> findAllByMemberProfileIdExcludingArchived(UUID profileId, UUID tenantId);

    List<Project> findAllByTeamId(UUID teamId, UUID tenantId);
}
