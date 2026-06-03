package com.epm.project.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.epm.project.domain.model.Project;
import com.epm.project.domain.model.ProjectMember;
import com.epm.project.domain.model.ProjectRole;
import com.epm.project.domain.model.ProjectStatus;
import com.epm.project.domain.model.ProjectTeam;
import com.epm.project.domain.model.ProjectVisibility;
import com.epm.project.domain.port.out.ProjectRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link ProjectRepository} port using JPA.
 *
 * <p>Reconstitutes the {@link Project} aggregate with all its members and teams.
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
        projectJpaRepo.save(toProjectEntity(project));
        for (ProjectTeam t : project.getTeams()) {
            teamJpaRepo.save(toTeamEntity(t, project.getTenantId()));
        }
        for (ProjectMember m : project.getMembers()) {
            memberJpaRepo.save(toMemberEntity(m, project.getTenantId()));
        }
        return findByIdAndTenantId(project.getId(), project.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Failed to reload project after save"));
    }

    @Override
    public List<Project> findAllByMemberProfileId(UUID profileId, UUID tenantId) {
        return projectJpaRepo.findAllProjectsByMemberProfileId(profileId, tenantId).stream()
                .map(this::reconstitute)
                .toList();
    }

    @Override
    public List<Project> findAllByMemberProfileIdExcludingArchived(UUID profileId, UUID tenantId) {
        return projectJpaRepo.findAllProjectsByMemberProfileIdExcludingArchived(profileId, tenantId).stream()
                .map(this::reconstitute)
                .toList();
    }

    @Override
    public List<Project> findAllByTeamId(UUID teamId, UUID tenantId) {
        return teamJpaRepo.findByTeamIdAndOrphanedAtIsNull(teamId).stream()
                .filter(pt -> pt.getTenantId().equals(tenantId))
                .map(pt -> findByIdAndTenantId(pt.getProjectId(), pt.getTenantId()).orElse(null))
                .filter(Objects::nonNull)
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
