package com.epm.notification.infrastructure.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.epm.notification.domain.model.UserEmailCache;
import com.epm.notification.domain.port.out.UserEmailCacheRepository;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing {@link UserEmailCacheRepository}.
 *
 * <p>Upsert is achieved by always calling JPA {@code save()} — the entity uses
 * the userId as the primary key, so saving an existing userId overwrites the row.
 */
@Component
public class UserEmailCacheRepositoryAdapter implements UserEmailCacheRepository {

    private final UserEmailCacheJpaRepository jpaRepository;

    public UserEmailCacheRepositoryAdapter(UserEmailCacheJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<UserEmailCache> findByUserId(UUID userId) {
        return jpaRepository.findById(userId).map(this::toDomain);
    }

    @Override
    public void save(UserEmailCache userEmailCache) {
        jpaRepository.save(toEntity(userEmailCache));
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private UserEmailCacheJpaEntity toEntity(UserEmailCache domain) {
        UserEmailCacheJpaEntity entity = new UserEmailCacheJpaEntity();
        entity.setUserId(domain.getUserId());
        entity.setTenantId(domain.getTenantId());
        entity.setEmail(domain.getEmail());
        entity.setUpdatedAt(domain.getUpdatedAt() != null ? domain.getUpdatedAt() : LocalDateTime.now());
        return entity;
    }

    private UserEmailCache toDomain(UserEmailCacheJpaEntity entity) {
        return new UserEmailCache(
                entity.getUserId(),
                entity.getTenantId(),
                entity.getEmail(),
                entity.getUpdatedAt());
    }
}
