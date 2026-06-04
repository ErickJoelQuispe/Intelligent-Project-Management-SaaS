package com.epm.notification.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link UserEmailCacheJpaEntity}.
 */
public interface UserEmailCacheJpaRepository extends JpaRepository<UserEmailCacheJpaEntity, UUID> {
}
