package com.epm.user.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link TeamMembershipJpaEntity}.
 */
public interface TeamMembershipJpaRepository extends JpaRepository<TeamMembershipJpaEntity, UUID> {

    List<TeamMembershipJpaEntity> findByTeamIdAndRemovedAtIsNull(UUID teamId);

    /**
     * Batch-fetches active memberships for multiple teams in a single query,
     * eliminating the N+1 problem in the list-teams flow.
     *
     * @param teamIds the set of team IDs to fetch memberships for
     * @return all active memberships for the given team IDs
     */
    List<TeamMembershipJpaEntity> findByTeamIdInAndRemovedAtIsNull(Collection<UUID> teamIds);

    boolean existsByTeamIdAndUserIdAndRemovedAtIsNull(UUID teamId, UUID userId);

    long countByTeamIdAndRoleAndRemovedAtIsNull(UUID teamId, String role);
}
