package com.epm.user.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link TeamMembershipJpaEntity}.
 */
public interface TeamMembershipJpaRepository extends JpaRepository<TeamMembershipJpaEntity, UUID> {

    List<TeamMembershipJpaEntity> findByTeamIdAndRemovedAtIsNull(UUID teamId);

    boolean existsByTeamIdAndUserIdAndRemovedAtIsNull(UUID teamId, UUID userId);

    long countByTeamIdAndRoleAndRemovedAtIsNull(UUID teamId, String role);
}
