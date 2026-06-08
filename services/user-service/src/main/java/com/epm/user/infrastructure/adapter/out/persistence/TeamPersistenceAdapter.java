package com.epm.user.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.Team;
import com.epm.user.domain.model.TeamMembership;
import com.epm.user.domain.model.TeamRole;
import com.epm.user.domain.port.out.TeamRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link TeamRepository} port using JPA.
 *
 * <p>Reconstitutes the Team aggregate with all its memberships.
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
    public Team save(Team team) {
        TeamJpaEntity teamEntity = toTeamEntity(team);
        teamJpaRepository.save(teamEntity);

        // Save/update all memberships
        for (TeamMembership membership : team.getMemberships()) {
            TeamMembershipJpaEntity membershipEntity = toMembershipEntity(membership, team.getTenantId());
            membershipJpaRepository.save(membershipEntity);
        }

        return findByIdAndTenantId(team.getId(), team.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Failed to reload team after save"));
    }

    @Override
    public List<Team> findAllByMemberUserId(UUID userId, UUID tenantId) {
        return teamJpaRepository.findAllTeamsByMemberUserId(userId, tenantId).stream()
                .map(this::reconstitute)
                .toList();
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private Team reconstitute(TeamJpaEntity entity) {
        List<TeamMembershipJpaEntity> membershipEntities =
                membershipJpaRepository.findByTeamIdAndRemovedAtIsNull(entity.getId());
        // Also include removed memberships to preserve aggregate integrity
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

    private TeamJpaEntity toTeamEntity(Team team) {
        TeamJpaEntity entity = new TeamJpaEntity();
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
        return entity;
    }

    private TeamMembershipJpaEntity toMembershipEntity(TeamMembership membership, UUID tenantId) {
        TeamMembershipJpaEntity entity = new TeamMembershipJpaEntity();
        entity.setId(membership.getId());
        entity.setTeamId(membership.getTeamId());
        entity.setUserId(membership.getUserId());
        entity.setRole(membership.getRole().name());
        entity.setJoinedAt(membership.getJoinedAt());
        entity.setRemovedAt(membership.getRemovedAt());
        entity.setTenantId(tenantId);
        entity.setCreatedAt(membership.getJoinedAt());
        entity.setUpdatedAt(membership.getRemovedAt() != null ? membership.getRemovedAt() : membership.getJoinedAt());
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        return entity;
    }
}
