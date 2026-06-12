package com.epm.user.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.out.UserProfileRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link UserProfileRepository} port using JPA.
 *
 * <p>Maps between {@link UserProfile} domain object and {@link UserProfileJpaEntity}.
 */
@Component
public class UserProfilePersistenceAdapter implements UserProfileRepository {

    private final UserProfileJpaRepository jpaRepository;

    public UserProfilePersistenceAdapter(UserProfileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<UserProfile> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .map(this::toDomain);
    }

    @Override
    public List<UserProfile> findAllByTenantId(UUID tenantId) {
        return jpaRepository.findAllByTenantIdAndDeletedAtIsNull(tenantId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public UserProfile save(UserProfile profile) {
        UserProfileJpaEntity entity = toEntity(profile);
        UserProfileJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public boolean existsByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.existsByIdAndTenantIdAndDeletedAtIsNull(id, tenantId);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private UserProfileJpaEntity toEntity(UserProfile profile) {
        UserProfileJpaEntity entity = new UserProfileJpaEntity();
        entity.setId(profile.getId());
        entity.setTenantId(profile.getTenantId());
        entity.setEmail(profile.getEmail());
        entity.setFirstName(profile.getFirstName());
        entity.setLastName(profile.getLastName());
        entity.setBio(profile.getBio());
        entity.setAvatarUrl(profile.getAvatarUrl());
        entity.setCreatedAt(profile.getCreatedAt() != null ? profile.getCreatedAt() : Instant.now());
        entity.setUpdatedAt(profile.getUpdatedAt() != null ? profile.getUpdatedAt() : Instant.now());
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        entity.setVersion(profile.getVersion());
        entity.setDeletedAt(profile.getDeletedAt());
        return entity;
    }

    private UserProfile toDomain(UserProfileJpaEntity entity) {
        return UserProfile.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmail(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getBio(),
                entity.getAvatarUrl(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }
}
