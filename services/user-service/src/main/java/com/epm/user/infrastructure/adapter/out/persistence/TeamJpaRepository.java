package com.epm.user.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link TeamJpaEntity}.
 */
public interface TeamJpaRepository extends JpaRepository<TeamJpaEntity, UUID> {

    Optional<TeamJpaEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    /**
     * Finds all teams where the given user has an active membership (removed_at IS NULL).
     */
    @Query("SELECT DISTINCT t FROM TeamJpaEntity t " +
           "JOIN TeamMembershipJpaEntity m ON m.teamId = t.id " +
           "WHERE m.userId = :userId AND m.tenantId = :tenantId " +
           "AND m.removedAt IS NULL AND t.deletedAt IS NULL")
    List<TeamJpaEntity> findAllTeamsByMemberUserId(
            @Param("userId") UUID userId,
            @Param("tenantId") UUID tenantId);
}
