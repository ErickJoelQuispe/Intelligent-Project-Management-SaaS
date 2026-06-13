package com.epm.user.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link UserProfileJpaEntity}.
 */
public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, UUID> {

    Optional<UserProfileJpaEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    List<UserProfileJpaEntity> findAllByTenantIdAndDeletedAtIsNull(UUID tenantId);

    /**
     * Paginated query for tenant users — pushes the LIMIT/OFFSET to the database
     * so the service never loads more rows than requested.
     *
     * @param tenantId the tenant to query
     * @param pageable page and size constraints
     * @return a page of matching user profile entities
     */
    Page<UserProfileJpaEntity> findAllByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
