package com.epm.user.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * TeamMembership entity — belongs to the {@link Team} aggregate.
 *
 * <p>Soft-deleted via {@code removedAt} field. An active membership has
 * {@code removedAt == null}.
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public class TeamMembership {

    private final UUID id;
    private final UUID teamId;
    private final UUID userId;
    private TeamRole role;
    private final Instant joinedAt;
    private Instant removedAt;

    // ── Factories ────────────────────────────────────────────────────────────

    /**
     * Creates an OWNER membership for the team creator.
     */
    public static TeamMembership createOwner(UUID teamId, UUID userId) {
        return new TeamMembership(UuidCreator.getTimeOrderedEpoch(), teamId, userId,
                TeamRole.OWNER, Instant.now(), null);
    }

    /**
     * Creates a membership with the specified role.
     */
    public static TeamMembership create(UUID teamId, UUID userId, TeamRole role) {
        return new TeamMembership(UuidCreator.getTimeOrderedEpoch(), teamId, userId,
                role, Instant.now(), null);
    }

    /**
     * Reconstitutes a membership from persistence.
     */
    public static TeamMembership reconstitute(UUID id, UUID teamId, UUID userId, TeamRole role,
            Instant joinedAt, Instant removedAt) {
        return new TeamMembership(id, teamId, userId, role, joinedAt, removedAt);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private TeamMembership(UUID id, UUID teamId, UUID userId, TeamRole role,
            Instant joinedAt, Instant removedAt) {
        this.id = id;
        this.teamId = teamId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.removedAt = removedAt;
    }

    // ── Business methods ─────────────────────────────────────────────────────

    /**
     * Changes the role of this membership.
     *
     * @param newRole the new role — MUST NOT be {@link TeamRole#OWNER}
     * @throws IllegalArgumentException if newRole is OWNER
     */
    public void changeRole(TeamRole newRole) {
        if (newRole == TeamRole.OWNER) {
            throw new IllegalArgumentException("Cannot assign OWNER role via changeRole");
        }
        this.role = newRole;
    }

    /**
     * Soft-removes this membership.
     */
    public void remove() {
        this.removedAt = Instant.now();
    }

    /**
     * Returns {@code true} if this membership is still active.
     */
    public boolean isActive() {
        return removedAt == null;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getTeamId() { return teamId; }

    public UUID getUserId() { return userId; }

    public TeamRole getRole() { return role; }

    public Instant getJoinedAt() { return joinedAt; }

    public Instant getRemovedAt() { return removedAt; }
}
