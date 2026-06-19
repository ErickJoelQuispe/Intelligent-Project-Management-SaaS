package com.epm.project.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.epm.project.domain.event.ProjectArchived;
import com.epm.project.domain.event.ProjectCreated;
import com.epm.project.domain.event.ProjectUpdated;
import com.epm.project.domain.event.TeamAssignedToProject;
import com.epm.project.domain.exception.DuplicateProjectMemberException;
import com.epm.project.domain.exception.DuplicateTeamAssignmentException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Project aggregate root.
 *
 * <p>A project belongs to a tenant and has an owner. Team assignments and member
 * registrations are managed within the aggregate.
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public class Project {

    private final UUID id;
    private final UUID tenantId;
    private final UUID ownerProfileId;
    private String name;
    private String description;
    private ProjectStatus status;
    private ProjectVisibility visibility;
    private final List<ProjectMember> members = new ArrayList<>();
    private final List<ProjectTeam> teams = new ArrayList<>();
    private final List<Object> domainEvents = new ArrayList<>();
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private long version;

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new project. Automatically adds the caller as an OWNER member
     * and records a {@link ProjectCreated} domain event.
     *
     * @param command the creation command with name, visibility, callerProfileId, tenantId
     * @return a new Project in ACTIVE status
     * @throws IllegalArgumentException if name is blank or exceeds 100 characters
     */
    public static Project create(CreateProjectCommand command) {
        guardName(command.name());
        Instant now = Instant.now();
        UUID projectId = UuidCreator.getTimeOrderedEpoch();
        Project project = new Project(
                projectId,
                command.tenantId(),
                command.callerProfileId(),
                command.name(),
                command.description(),
                ProjectStatus.ACTIVE,
                command.visibility(),
                now,
                now,
                null,
                0L);
        project.members.add(ProjectMember.createOwner(projectId, command.callerProfileId()));
        project.domainEvents.add(new ProjectCreated(
                UuidCreator.getTimeOrderedEpoch(),
                projectId,
                command.name(),
                command.callerProfileId(),
                command.tenantId(),
                now));
        return project;
    }

    /**
     * Reconstitutes a Project from persistence (no events raised).
     */
    public static Project reconstitute(UUID id, UUID tenantId, UUID ownerProfileId,
            String name, String description, ProjectStatus status, ProjectVisibility visibility,
            List<ProjectMember> members, List<ProjectTeam> teams,
            Instant createdAt, Instant updatedAt, Instant deletedAt, long version) {
        Project project = new Project(id, tenantId, ownerProfileId, name, description,
                status, visibility, createdAt, updatedAt, deletedAt, version);
        project.members.addAll(members);
        project.teams.addAll(teams);
        return project;
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private Project(UUID id, UUID tenantId, UUID ownerProfileId,
            String name, String description, ProjectStatus status, ProjectVisibility visibility,
            Instant createdAt, Instant updatedAt, Instant deletedAt, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.ownerProfileId = ownerProfileId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.visibility = visibility;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.version = version;
    }

    // ── Business methods ─────────────────────────────────────────────────────

    /**
     * Updates the project name, description, and visibility.
     *
     * @param name          new name
     * @param description   new description (nullable)
     * @param visibility    new visibility
     * @param callerProfileId the profile making the change (must be OWNER or MANAGER)
     * @throws UnauthorizedProjectAccessException if caller lacks OWNER or MANAGER role
     */
    public void update(String name, String description, ProjectVisibility visibility,
            UUID callerProfileId) {
        guardRole(callerProfileId, ProjectRole.OWNER, ProjectRole.MANAGER);
        guardName(name);
        this.name = name;
        this.description = description;
        this.visibility = visibility;
        this.updatedAt = Instant.now();
        domainEvents.add(new ProjectUpdated(
                UuidCreator.getTimeOrderedEpoch(), id, name, description, tenantId, Instant.now()));
    }

    /**
     * Archives this project. Only an OWNER may archive.
     *
     * <p>Idempotent: if the project is already ARCHIVED, this method is a no-op
     * and does NOT emit a duplicate {@link ProjectArchived} event. Authorization
     * is checked first so a non-owner cannot re-archive (authz before short-circuit).
     *
     * @param callerProfileId the profile requesting the archive
     * @throws UnauthorizedProjectAccessException if caller is not OWNER
     */
    public void archive(UUID callerProfileId) {
        guardRole(callerProfileId, ProjectRole.OWNER); // authz first — non-owners are always rejected
        if (this.status == ProjectStatus.ARCHIVED) {
            return; // idempotent — no duplicate event emitted
        }
        this.status = ProjectStatus.ARCHIVED;
        this.updatedAt = Instant.now();
        domainEvents.add(new ProjectArchived(
                UuidCreator.getTimeOrderedEpoch(), id, this.name, this.ownerProfileId, tenantId, Instant.now()));
    }

    /**
     * Assigns a team to this project.
     *
     * @param teamId          the team to assign
     * @param callerProfileId the profile making the assignment (must be OWNER or MANAGER)
     * @throws UnauthorizedProjectAccessException   if caller lacks sufficient role
     * @throws DuplicateTeamAssignmentException     if team is already actively assigned
     */
    public void assignTeam(UUID teamId, UUID callerProfileId) {
        guardRole(callerProfileId, ProjectRole.OWNER, ProjectRole.MANAGER);
        guardNoDuplicateTeam(teamId);
        teams.add(ProjectTeam.create(id, teamId));
        List<UUID> activeMemberIds = members.stream()
                .filter(ProjectMember::isActive)
                .map(ProjectMember::getProfileId)
                .toList();
        domainEvents.add(new TeamAssignedToProject(
                UuidCreator.getTimeOrderedEpoch(), id, teamId, activeMemberIds, tenantId, Instant.now()));
    }

    /**
     * Marks a team assignment as orphaned (team was deleted in user-service).
     *
     * @param teamId the team that was deleted
     */
    public void removeTeam(UUID teamId) {
        teams.stream()
                .filter(t -> t.getTeamId().equals(teamId) && t.isActive())
                .findFirst()
                .ifPresent(ProjectTeam::markOrphaned);
        this.updatedAt = Instant.now();
    }

    /**
     * Adds a member to the project.
     *
     * <p>Only an OWNER or MANAGER may add members (authorization guard added as FIX 6).
     *
     * @param profileId       the profile to add
     * @param role            the role to assign
     * @param callerProfileId the profile performing the action (must be OWNER or MANAGER)
     * @throws UnauthorizedProjectAccessException if caller lacks OWNER or MANAGER role
     * @throws DuplicateProjectMemberException    if profile is already an active member
     */
    public void addMember(UUID profileId, ProjectRole role, UUID callerProfileId) {
        guardRole(callerProfileId, ProjectRole.OWNER, ProjectRole.MANAGER);
        guardNoDuplicateMember(profileId);
        members.add(ProjectMember.create(id, profileId, role));
    }

    /**
     * Removes a member from the project (soft-delete).
     *
     * @param profileId the profile to remove
     */
    public void removeMember(UUID profileId) {
        members.stream()
                .filter(m -> m.getProfileId().equals(profileId) && m.isActive())
                .findFirst()
                .ifPresent(ProjectMember::remove);
        this.updatedAt = Instant.now();
    }

    /**
     * Returns {@code true} if the given profile is an active member of this project.
     */
    public boolean isMember(UUID profileId) {
        return members.stream()
                .anyMatch(m -> m.getProfileId().equals(profileId) && m.isActive());
    }

    /**
     * Determines whether the caller may access this project, applying visibility semantics:
     * <ul>
     *   <li>{@link ProjectVisibility#PUBLIC}: accessible to everyone.</li>
     *   <li>{@link ProjectVisibility#PRIVATE}: accessible only to direct members.</li>
     *   <li>{@link ProjectVisibility#TEAM}: accessible to direct members OR to any user
     *       whose team is actively assigned to this project (i.e., {@code callerTeamIds}
     *       overlaps with the project's active team assignments).</li>
     * </ul>
     *
     * <p>The {@code callerTeamIds} set comes from the caller's JWT or a lookup in
     * user-service. Passing an empty set for TEAM projects means the caller is treated
     * as PRIVATE until team-claim sourcing is wired.
     *
     * @param callerProfileId the profile requesting access
     * @param callerTeamIds   the set of teams the caller belongs to (may be empty)
     * @return {@code true} if the caller is allowed to see this project
     */
    public boolean canBeAccessedBy(UUID callerProfileId, Set<UUID> callerTeamIds) {
        if (visibility == ProjectVisibility.PUBLIC) {
            return true;
        }
        if (isMember(callerProfileId)) {
            return true;
        }
        if (visibility == ProjectVisibility.TEAM && callerTeamIds != null) {
            return teams.stream()
                    .anyMatch(t -> t.isActive() && callerTeamIds.contains(t.getTeamId()));
        }
        return false;
    }

    /**
     * Returns {@code true} if the given profile has any of the specified roles.
     */
    public boolean hasRole(UUID profileId, ProjectRole... roles) {
        for (ProjectRole role : roles) {
            boolean matches = members.stream()
                    .anyMatch(m -> m.getProfileId().equals(profileId)
                            && m.isActive()
                            && m.getRole() == role);
            if (matches) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all pending domain events and clears the internal list.
     */
    public List<Object> pullDomainEvents() {
        List<Object> copy = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return copy;
    }

    // ── Private guards ────────────────────────────────────────────────────────

    private static void guardName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name must not be blank");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Project name must not exceed 100 characters");
        }
    }

    private void guardRole(UUID callerProfileId, ProjectRole... requiredRoles) {
        if (!hasRole(callerProfileId, requiredRoles)) {
            throw new UnauthorizedProjectAccessException(callerProfileId, id);
        }
    }

    private void guardNoDuplicateTeam(UUID teamId) {
        boolean alreadyAssigned = teams.stream()
                .anyMatch(t -> t.getTeamId().equals(teamId) && t.isActive());
        if (alreadyAssigned) {
            throw new DuplicateTeamAssignmentException(teamId, id);
        }
    }

    private void guardNoDuplicateMember(UUID profileId) {
        boolean alreadyActive = members.stream()
                .anyMatch(m -> m.getProfileId().equals(profileId) && m.isActive());
        if (alreadyActive) {
            throw new DuplicateProjectMemberException(profileId, id);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }

    public UUID getOwnerProfileId() { return ownerProfileId; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public ProjectStatus getStatus() { return status; }

    public ProjectVisibility getVisibility() { return visibility; }

    public List<ProjectMember> getMembers() { return Collections.unmodifiableList(members); }

    public List<ProjectTeam> getTeams() { return Collections.unmodifiableList(teams); }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }

    public long getVersion() { return version; }
}
