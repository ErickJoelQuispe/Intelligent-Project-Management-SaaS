package com.epm.user.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link UserProfileJpaEntity}.
 */
public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, UUID> {

    Optional<UserProfileJpaEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    boolean existsByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
