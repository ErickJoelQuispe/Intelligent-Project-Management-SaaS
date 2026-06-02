package com.epm.project.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Value object representing a member of a project.
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public class ProjectMember {

    private final UUID id;
    private final UUID projectId;
    private final UUID profileId;
    private final ProjectRole role;
    private final Instant joinedAt;
    private Instant removedAt;

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new project member with the specified role.
     */
    public static ProjectMember create(UUID projectId, UUID profileId, ProjectRole role) {
        Instant now = Instant.now();
        return new ProjectMember(UuidCreator.getTimeOrderedEpoch(), projectId, profileId, role, now, null);
    }

    /**
     * Creates a new project member with the OWNER role.
     */
    public static ProjectMember createOwner(UUID projectId, UUID profileId) {
        return create(projectId, profileId, ProjectRole.OWNER);
    }

    /**
     * Reconstitutes a ProjectMember from persistence (no events raised).
     */
    public static ProjectMember reconstitute(UUID id, UUID projectId, UUID profileId,
            ProjectRole role, Instant joinedAt, Instant removedAt) {
        return new ProjectMember(id, projectId, profileId, role, joinedAt, removedAt);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private ProjectMember(UUID id, UUID projectId, UUID profileId, ProjectRole role,
            Instant joinedAt, Instant removedAt) {
        this.id = id;
        this.projectId = projectId;
        this.profileId = profileId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.removedAt = removedAt;
    }

    // ── Business methods ─────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this membership is currently active (not removed).
     */
    public boolean isActive() {
        return removedAt == null;
    }

    /**
     * Marks this membership as removed (soft-delete).
     */
    public void remove() {
        this.removedAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getProjectId() { return projectId; }

    public UUID getProfileId() { return profileId; }

    public ProjectRole getRole() { return role; }

    public Instant getJoinedAt() { return joinedAt; }

    public Instant getRemovedAt() { return removedAt; }
}
