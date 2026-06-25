package com.epm.project.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.epm.project.domain.model.Project;
import com.epm.project.domain.model.ProjectMember;
import com.epm.project.domain.model.ProjectRole;
import com.epm.project.domain.model.ProjectStatus;
import com.epm.project.domain.model.ProjectTeam;
import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.out.ProjectRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link ProjectRepository} port using JPA.
 *
 * <p>Reconstitutes the {@link Project} aggregate with all its members and teams.
 *
 * <p>List operations ({@link #findAllByMemberProfileId}, {@link #findAllByTeamId}) use
 * batch-loading (IN queries) to avoid the N+1 fan-out that was present before FIX 8:
 * instead of 3 extra queries per project, members and teams are fetched in two bulk
 * queries and grouped in memory.
 */
@Component
public class ProjectPersistenceAdapter implements ProjectRepository {

    private final ProjectJpaRepository projectJpaRepo;
    private final ProjectTeamJpaRepository teamJpaRepo;
    private final ProjectMemberJpaRepository memberJpaRepo;

    public ProjectPersistenceAdapter(ProjectJpaRepository projectJpaRepo,
            ProjectTeamJpaRepository teamJpaRepo,
            ProjectMemberJpaRepository memberJpaRepo) {
        this.projectJpaRepo = projectJpaRepo;
        this.teamJpaRepo = teamJpaRepo;
        this.memberJpaRepo = memberJpaRepo;
    }

    @Override
    public Optional<Project> findByIdAndTenantId(UUID id, UUID tenantId) {
        return projectJpaRepo.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .map(this::reconstitute);
    }

    @Override
    public Project save(Project project) {
        // Use a direct UPDATE query for existing rows to avoid @Version merge conflicts.
        // For new rows (insert), fall back to the standard save path.
        Instant updatedAt = project.getUpdatedAt() != null ? project.getUpdatedAt() : Instant.now();
        int updated = projectJpaRepo.updateFields(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getVisibility().name(),
                updatedAt,
                project.getDeletedAt());
        if (updated == 0) {
            projectJpaRepo.save(toProjectEntity(project));
        }
        for (ProjectTeam t : project.getTeams()) {
            // Only insert new teams — never overwrite existing rows to avoid stale-version conflicts.
            if (!teamJpaRepo.existsById(t.getId())) {
                teamJpaRepo.save(toTeamEntity(t, project.getTenantId()));
            }
        }
        for (ProjectMember m : project.getMembers()) {
            // Only insert new members — never overwrite existing rows to avoid stale-version conflicts.
            // Existing member mutations (e.g. soft-delete via removeMember) are handled by dedicated
            // repository calls, not through the aggregate save path.
            if (!memberJpaRepo.existsById(m.getId())) {
                memberJpaRepo.save(toMemberEntity(m, project.getTenantId()));
            }
        }
        return findByIdAndTenantId(project.getId(), project.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Failed to reload project after save"));
    }

    @Override
    public List<Project> findAllByMemberProfileId(UUID profileId, UUID tenantId) {
        List<ProjectJpaEntity> projectEntities =
                projectJpaRepo.findAllProjectsByMemberProfileId(profileId, tenantId);
        return reconstituteBatch(projectEntities);
    }

    @Override
    public List<Project> findPageByMemberProfileId(UUID profileId, UUID tenantId, int page, int size) {
        // LIMIT/OFFSET delegated to the database via PageRequest — no in-memory truncation.
        // The batch-load of members/teams then operates only on this page's project IDs.
        List<ProjectJpaEntity> projectEntities =
                projectJpaRepo.findPageByMemberProfileId(profileId, tenantId, PageRequest.of(page, size));
        return reconstituteBatch(projectEntities);
    }

    @Override
    public List<Project> findAllByMemberProfileIdExcludingArchived(UUID profileId, UUID tenantId) {
        List<ProjectJpaEntity> projectEntities =
                projectJpaRepo.findAllProjectsByMemberProfileIdExcludingArchived(profileId, tenantId);
        return reconstituteBatch(projectEntities);
    }

    @Override
    public List<Project> findPageByMemberProfileIdExcludingArchived(
            UUID profileId, UUID tenantId, int page, int size) {
        // LIMIT/OFFSET delegated to the database via PageRequest — no in-memory truncation.
        // The batch-load of members/teams then operates only on this page's project IDs.
        List<ProjectJpaEntity> projectEntities = projectJpaRepo
                .findPageByMemberProfileIdExcludingArchived(profileId, tenantId, PageRequest.of(page, size));
        return reconstituteBatch(projectEntities);
    }

    @Override
    public List<Project> findAllByTeamId(UUID teamId, UUID tenantId) {
        // FIX 8 + FIX 19: push tenant filter into SQL; fetch all matching project IDs in one query.
        List<UUID> projectIds = teamJpaRepo.findByTeamIdAndTenantIdAndOrphanedAtIsNull(teamId, tenantId)
                .stream()
                .map(ProjectTeamJpaEntity::getProjectId)
                .toList();

        if (projectIds.isEmpty()) {
            return List.of();
        }

        List<ProjectJpaEntity> projectEntities = projectJpaRepo.findAllById(projectIds)
                .stream()
                .filter(p -> p.getDeletedAt() == null)
                .toList();

        return reconstituteBatch(projectEntities);
    }

    // ── Batch reconstitution (FIX 8) ─────────────────────────────────────────

    /**
     * Reconstitutes multiple projects using two bulk queries instead of N×3 per-project
     * queries. Members and teams for all projects are loaded in one IN query each, then
     * grouped by projectId in memory.
     */
    private List<Project> reconstituteBatch(List<ProjectJpaEntity> projectEntities) {
        if (projectEntities.isEmpty()) {
            return List.of();
        }

        List<UUID> projectIds = projectEntities.stream()
                .map(ProjectJpaEntity::getId)
                .toList();

        // Batch-load all active teams and members for the given project IDs.
        Map<UUID, List<ProjectTeamJpaEntity>> teamsByProject =
                teamJpaRepo.findByProjectIdInAndOrphanedAtIsNull(projectIds)
                        .stream()
                        .collect(Collectors.groupingBy(ProjectTeamJpaEntity::getProjectId));

        Map<UUID, List<ProjectMemberJpaEntity>> membersByProject =
                memberJpaRepo.findByProjectIdInAndRemovedAtIsNull(projectIds)
                        .stream()
                        .collect(Collectors.groupingBy(ProjectMemberJpaEntity::getProjectId));

        return projectEntities.stream()
                .map(entity -> {
                    List<ProjectTeam> teams = teamsByProject
                            .getOrDefault(entity.getId(), List.of())
                            .stream()
                            .map(this::toTeamDomain)
                            .toList();
                    List<ProjectMember> members = membersByProject
                            .getOrDefault(entity.getId(), List.of())
                            .stream()
                            .map(this::toMemberDomain)
                            .toList();
                    return reconstitute(entity, members, teams);
                })
                .toList();
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private Project reconstitute(ProjectJpaEntity entity) {
        List<ProjectTeam> teams = teamJpaRepo.findByProjectIdAndOrphanedAtIsNull(entity.getId())
                .stream()
                .map(this::toTeamDomain)
                .toList();
        List<ProjectMember> members = memberJpaRepo.findByProjectIdAndRemovedAtIsNull(entity.getId())
                .stream()
                .map(this::toMemberDomain)
                .toList();
        return reconstitute(entity, members, teams);
    }

    private Project reconstitute(ProjectJpaEntity entity,
            List<ProjectMember> members, List<ProjectTeam> teams) {
        return Project.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getOwnerId(),
                entity.getName(),
                entity.getDescription(),
                ProjectStatus.valueOf(entity.getStatus()),
                ProjectVisibility.valueOf(entity.getVisibility()),
                members,
                teams,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                entity.getVersion());
    }

    private ProjectTeam toTeamDomain(ProjectTeamJpaEntity entity) {
        return ProjectTeam.reconstitute(
                entity.getId(),
                entity.getProjectId(),
                entity.getTeamId(),
                entity.getAssignedAt(),
                entity.getOrphanedAt());
    }

    private ProjectMember toMemberDomain(ProjectMemberJpaEntity entity) {
        return ProjectMember.reconstitute(
                entity.getId(),
                entity.getProjectId(),
                entity.getProfileId(),
                ProjectRole.valueOf(entity.getRole()),
                entity.getJoinedAt(),
                entity.getRemovedAt());
    }

    private ProjectJpaEntity toProjectEntity(Project project) {
        ProjectJpaEntity entity = new ProjectJpaEntity();
        entity.setId(project.getId());
        entity.setTenantId(project.getTenantId());
        entity.setOwnerId(project.getOwnerProfileId());
        entity.setName(project.getName());
        entity.setDescription(project.getDescription());
        entity.setStatus(project.getStatus().name());
        entity.setVisibility(project.getVisibility().name());
        entity.setCreatedAt(project.getCreatedAt() != null ? project.getCreatedAt() : Instant.now());
        entity.setUpdatedAt(project.getUpdatedAt() != null ? project.getUpdatedAt() : Instant.now());
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        entity.setVersion(project.getVersion());
        entity.setDeletedAt(project.getDeletedAt());
        return entity;
    }

    private ProjectTeamJpaEntity toTeamEntity(ProjectTeam team, UUID tenantId) {
        ProjectTeamJpaEntity entity = new ProjectTeamJpaEntity();
        entity.setId(team.getId());
        entity.setProjectId(team.getProjectId());
        entity.setTeamId(team.getTeamId());
        entity.setAssignedAt(team.getAssignedAt() != null ? team.getAssignedAt() : Instant.now());
        entity.setOrphanedAt(team.getOrphanedAt());
        entity.setTenantId(tenantId);
        entity.setCreatedAt(team.getAssignedAt() != null ? team.getAssignedAt() : Instant.now());
        entity.setUpdatedAt(team.getOrphanedAt() != null ? team.getOrphanedAt() : Instant.now());
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        return entity;
    }

    private ProjectMemberJpaEntity toMemberEntity(ProjectMember member, UUID tenantId) {
        ProjectMemberJpaEntity entity = new ProjectMemberJpaEntity();
        entity.setId(member.getId());
        entity.setProjectId(member.getProjectId());
        entity.setProfileId(member.getProfileId());
        entity.setRole(member.getRole().name());
        entity.setJoinedAt(member.getJoinedAt() != null ? member.getJoinedAt() : Instant.now());
        entity.setRemovedAt(member.getRemovedAt());
        entity.setTenantId(tenantId);
        entity.setCreatedAt(member.getJoinedAt() != null ? member.getJoinedAt() : Instant.now());
        entity.setUpdatedAt(member.getRemovedAt() != null ? member.getRemovedAt() : Instant.now());
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        return entity;
    }
}
