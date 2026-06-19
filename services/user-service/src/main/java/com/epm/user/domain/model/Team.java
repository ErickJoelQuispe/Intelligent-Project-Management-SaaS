package com.epm.user.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.epm.user.domain.event.TeamCreated;
import com.epm.user.domain.event.TeamDeleted;
import com.epm.user.domain.event.TeamMemberJoined;
import com.epm.user.domain.event.TeamMemberLeft;
import com.epm.user.domain.exception.DuplicateMemberException;
import com.epm.user.domain.exception.LastOwnerException;
import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Team aggregate root.
 *
 * <p>A team belongs to a tenant and has an owner. Memberships are managed
 * within the aggregate. The last owner cannot be removed.
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public class Team {

    private final UUID id;
    private final UUID tenantId;
    private final UUID ownerId;
    private String name;
    private String description;
    private final List<TeamMembership> memberships = new ArrayList<>();
    private final List<Object> domainEvents = new ArrayList<>();
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new team. Automatically adds the creator as an OWNER member
     * and records a {@link TeamCreated} domain event.
     *
     * @param tenantId    the tenant this team belongs to
     * @param ownerId     the user creating the team (becomes first OWNER)
     * @param name        team name (max 100 chars)
     * @param description optional description
     * @return a new Team
     */
    public static Team create(UUID tenantId, UUID ownerId, String name, String description) {
        Instant now = Instant.now();
        UUID teamId = UuidCreator.getTimeOrderedEpoch();
        Team team = new Team(teamId, tenantId, ownerId, name, description, now, now, null);
        team.memberships.add(TeamMembership.createOwner(teamId, ownerId));
        team.domainEvents.add(new TeamCreated(
                UuidCreator.getTimeOrderedEpoch(), teamId, tenantId, ownerId, name, now));
        return team;
    }

    /**
     * Reconstitutes a Team from persistence (no events raised).
     */
    public static Team reconstitute(UUID id, UUID tenantId, UUID ownerId, String name,
            String description, List<TeamMembership> memberships,
            Instant createdAt, Instant updatedAt, Instant deletedAt) {
        Team team = new Team(id, tenantId, ownerId, name, description, createdAt, updatedAt, deletedAt);
        team.memberships.addAll(memberships);
        return team;
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private Team(UUID id, UUID tenantId, UUID ownerId, String name, String description,
            Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    // ── Business methods ─────────────────────────────────────────────────────

    /**
     * Adds a new member to the team.
     *
     * @param userId the user to add
     * @param role   the membership role (MEMBER or VIEWER)
     * @throws DuplicateMemberException if the user is already an active member
     */
    public void addMember(UUID userId, TeamRole role) {
        boolean alreadyActive = memberships.stream()
                .anyMatch(m -> m.getUserId().equals(userId) && m.isActive());
        if (alreadyActive) {
            throw new DuplicateMemberException(userId);
        }
        TeamMembership membership = TeamMembership.create(id, userId, role);
        memberships.add(membership);
        domainEvents.add(new TeamMemberJoined(
                UuidCreator.getTimeOrderedEpoch(), id, tenantId, userId, role, this.name, Instant.now()));
    }

    /**
     * Removes a member from the team (soft-delete).
     *
     * @param userId the user to remove
     * @throws LastOwnerException if removing this user would leave no OWNER
     */
    public void removeMember(UUID userId) {
        TeamMembership target = memberships.stream()
                .filter(m -> m.getUserId().equals(userId) && m.isActive())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + userId));

        if (target.getRole() == TeamRole.OWNER) {
            long ownerCount = memberships.stream()
                    .filter(m -> m.getRole() == TeamRole.OWNER && m.isActive())
                    .count();
            if (ownerCount <= 1) {
                throw new LastOwnerException();
            }
        }

        target.remove();
        this.updatedAt = Instant.now();
        domainEvents.add(new TeamMemberLeft(
                UuidCreator.getTimeOrderedEpoch(), id, tenantId, userId, this.name, Instant.now()));
    }

    /**
     * Marks this team as deleted and emits a {@link TeamDeleted} domain event.
     *
     * <p>Sets {@code deletedAt} and {@code updatedAt} to the current instant.
     */
    public void delete() {
        Instant now = Instant.now();
        this.deletedAt = now;
        this.updatedAt = now;
        domainEvents.add(new TeamDeleted(
                UuidCreator.getTimeOrderedEpoch(), id, tenantId, now));
    }

    /**
     * Returns all pending domain events and clears the internal list.
     */
    public List<Object> pullDomainEvents() {
        List<Object> copy = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return copy;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }

    public UUID getOwnerId() { return ownerId; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public List<TeamMembership> getMemberships() { return Collections.unmodifiableList(memberships); }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
}
