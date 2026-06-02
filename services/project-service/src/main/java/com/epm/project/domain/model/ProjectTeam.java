package com.epm.project.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Value object representing a team assigned to a project.
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public class ProjectTeam {

    private final UUID id;
    private final UUID projectId;
    private final UUID teamId;
    private final Instant assignedAt;
    private Instant orphanedAt;

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new project-team association.
     */
    public static ProjectTeam create(UUID projectId, UUID teamId) {
        Instant now = Instant.now();
        return new ProjectTeam(UuidCreator.getTimeOrderedEpoch(), projectId, teamId, now, null);
    }

    /**
     * Reconstitutes a ProjectTeam from persistence (no events raised).
     */
    public static ProjectTeam reconstitute(UUID id, UUID projectId, UUID teamId,
            Instant assignedAt, Instant orphanedAt) {
        return new ProjectTeam(id, projectId, teamId, assignedAt, orphanedAt);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private ProjectTeam(UUID id, UUID projectId, UUID teamId, Instant assignedAt, Instant orphanedAt) {
        this.id = id;
        this.projectId = projectId;
        this.teamId = teamId;
        this.assignedAt = assignedAt;
        this.orphanedAt = orphanedAt;
    }

    // ── Business methods ─────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this team assignment is currently active (not orphaned).
     */
    public boolean isActive() {
        return orphanedAt == null;
    }

    /**
     * Marks this team assignment as orphaned (team was deleted in user-service).
     */
    public void markOrphaned() {
        this.orphanedAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getProjectId() { return projectId; }

    public UUID getTeamId() { return teamId; }

    public Instant getAssignedAt() { return assignedAt; }

    public Instant getOrphanedAt() { return orphanedAt; }
}
