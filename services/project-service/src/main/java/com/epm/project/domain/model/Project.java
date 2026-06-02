package com.epm.project.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
     * @param callerProfileId the profile requesting the archive
     * @throws UnauthorizedProjectAccessException if caller is not OWNER
     */
    public void archive(UUID callerProfileId) {
        guardRole(callerProfileId, ProjectRole.OWNER);
        this.status = ProjectStatus.ARCHIVED;
        this.updatedAt = Instant.now();
        domainEvents.add(new ProjectArchived(
                UuidCreator.getTimeOrderedEpoch(), id, tenantId, Instant.now()));
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
        domainEvents.add(new TeamAssignedToProject(
                UuidCreator.getTimeOrderedEpoch(), id, teamId, tenantId, Instant.now()));
    }

    /**
     * Adds a member to the project.
     *
     * @param profileId the profile to add
     * @param role      the role to assign
     * @throws DuplicateProjectMemberException if profile is already an active member
     */
    public void addMember(UUID profileId, ProjectRole role) {
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
