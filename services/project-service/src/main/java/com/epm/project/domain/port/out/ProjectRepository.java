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
     * Finds a page of projects where the given profile is a member.
     *
     * <p>LIMIT/OFFSET are pushed to SQL by the adapter — no in-memory truncation.
     *
     * @param profileId member's profile identifier
     * @param tenantId  tenant scope
     * @param page      zero-based page index (must be {@code >= 0})
     * @param size      page size (must be {@code >= 1})
     * @return a bounded list of matching projects
     */
    List<Project> findPageByMemberProfileId(UUID profileId, UUID tenantId, int page, int size);

    /**
     * Finds all active (non-archived) projects where the given profile is a member.
     *
     * @param profileId member's profile identifier
     * @param tenantId  tenant scope
     * @return list of non-archived projects
     */
    List<Project> findAllByMemberProfileIdExcludingArchived(UUID profileId, UUID tenantId);

    /**
     * Finds a page of active (non-archived) projects where the given profile is a member.
     *
     * <p>LIMIT/OFFSET are pushed to SQL by the adapter — no in-memory truncation.
     *
     * @param profileId member's profile identifier
     * @param tenantId  tenant scope
     * @param page      zero-based page index (must be {@code >= 0})
     * @param size      page size (must be {@code >= 1})
     * @return a bounded list of non-archived projects
     */
    List<Project> findPageByMemberProfileIdExcludingArchived(UUID profileId, UUID tenantId, int page, int size);

    List<Project> findAllByTeamId(UUID teamId, UUID tenantId);
}
