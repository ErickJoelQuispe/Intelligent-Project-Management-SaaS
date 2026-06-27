package com.epm.user.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamMembership;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.out.TeamRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter implementing {@link TeamRepository} port using JPA.
 *
 * <p>Reconstitutes the Team aggregate with all its memberships.
 *
 * <p>The list path ({@link #findAllByMemberUserId}) fetches memberships for all
 * returned teams in a single batch query to avoid the N+1 problem.
 */
@Component
public class TeamPersistenceAdapter implements TeamRepository {

    private final TeamJpaRepository teamJpaRepository;
    private final TeamMembershipJpaRepository membershipJpaRepository;

    public TeamPersistenceAdapter(TeamJpaRepository teamJpaRepository,
            TeamMembershipJpaRepository membershipJpaRepository) {
        this.teamJpaRepository = teamJpaRepository;
        this.membershipJpaRepository = membershipJpaRepository;
    }

    @Override
    public Optional<Team> findByIdAndTenantId(UUID teamId, UUID tenantId) {
        return teamJpaRepository.findByIdAndTenantIdAndDeletedAtIsNull(teamId, tenantId)
                .map(this::reconstitute);
    }

    @Override
    @Transactional
    public Team save(Team team) {
        TeamJpaEntity teamEntity = teamJpaRepository.findById(team.getId())
                .orElseGet(TeamJpaEntity::new);
        applyTeamFields(teamEntity, team);
        teamJpaRepository.save(teamEntity);

        // Save/update all memberships — load existing managed entity first to avoid
        // OptimisticLockException from @Version mismatch on detached entities.
        for (TeamMembership membership : team.getMemberships()) {
            TeamMembershipJpaEntity membershipEntity = membershipJpaRepository
                    .findById(membership.getId())
                    .orElseGet(TeamMembershipJpaEntity::new);
            applyMembershipFields(membershipEntity, membership, team.getTenantId());
            membershipJpaRepository.save(membershipEntity);
        }

        // Reload by ID only — NOT through the soft-delete filter. On a delete, the team
        // row now has deletedAt set, so findByIdAndTenantIdAndDeletedAtIsNull would return
        // empty and wrongly throw. Reload the actual persisted row regardless of its state.
        TeamJpaEntity reloaded = teamJpaRepository.findById(team.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to reload team after save: " + team.getId()));
        return reconstitute(reloaded);
    }

    /**
     * Lists all teams a user is an active member of.
     *
     * <p>Memberships for all returned teams are fetched in a <em>single</em> batch
     * query ({@code findByTeamIdInAndRemovedAtIsNull}) and grouped in memory,
     * eliminating the N+1 per-team membership query that the old implementation had.
     */
    @Override
    public List<Team> findAllByMemberUserId(UUID userId, UUID tenantId) {
        List<TeamJpaEntity> teamEntities =
                teamJpaRepository.findAllTeamsByMemberUserId(userId, tenantId);

        if (teamEntities.isEmpty()) {
            return List.of();
        }

        // Batch-fetch all memberships for the found teams in ONE query
        List<UUID> teamIds = teamEntities.stream().map(TeamJpaEntity::getId).toList();
        Map<UUID, List<TeamMembershipJpaEntity>> membershipsByTeam =
                membershipJpaRepository.findByTeamIdInAndRemovedAtIsNull(teamIds)
                        .stream()
                        .collect(Collectors.groupingBy(TeamMembershipJpaEntity::getTeamId));

        return teamEntities.stream()
                .map(entity -> reconstituteWithMemberships(
                        entity,
                        membershipsByTeam.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    /** Single-team reconstitution — issues one membership query per call (used for single-team paths). */
    private Team reconstitute(TeamJpaEntity entity) {
        List<TeamMembershipJpaEntity> membershipEntities =
                membershipJpaRepository.findByTeamIdAndRemovedAtIsNull(entity.getId());
        return reconstituteWithMemberships(entity, membershipEntities);
    }

    /** Reconstitution from pre-fetched memberships — no additional DB query. */
    private Team reconstituteWithMemberships(TeamJpaEntity entity,
            List<TeamMembershipJpaEntity> membershipEntities) {
        List<TeamMembership> memberships = membershipEntities.stream()
                .map(this::toMembershipDomain)
                .toList();
        return Team.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getOwnerId(),
                entity.getName(),
                entity.getDescription(),
                memberships,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    private TeamMembership toMembershipDomain(TeamMembershipJpaEntity entity) {
        return TeamMembership.reconstitute(
                entity.getId(),
                entity.getTeamId(),
                entity.getUserId(),
                TeamRole.valueOf(entity.getRole()),
                entity.getJoinedAt(),
                entity.getRemovedAt());
    }

    private void applyTeamFields(TeamJpaEntity entity, Team team) {
        entity.setId(team.getId());
        entity.setTenantId(team.getTenantId());
        entity.setOwnerId(team.getOwnerId());
        entity.setName(team.getName());
        entity.setDescription(team.getDescription());
        entity.setCreatedAt(team.getCreatedAt() != null ? team.getCreatedAt() : Instant.now());
        entity.setUpdatedAt(team.getUpdatedAt() != null ? team.getUpdatedAt() : Instant.now());
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        entity.setDeletedAt(team.getDeletedAt());
    }

    private void applyMembershipFields(TeamMembershipJpaEntity entity,
            TeamMembership membership, UUID tenantId) {
        entity.setId(membership.getId());
        entity.setTeamId(membership.getTeamId());
        entity.setUserId(membership.getUserId());
        entity.setRole(membership.getRole().name());
        entity.setJoinedAt(membership.getJoinedAt());
        entity.setRemovedAt(membership.getRemovedAt());
        entity.setTenantId(tenantId);
        entity.setCreatedAt(membership.getJoinedAt());
        entity.setUpdatedAt(Instant.now());
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
    }
}
