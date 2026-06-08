package com.epm.auth.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link SecurityEventJpaEntity}.
 */
public interface SecurityEventJpaRepository extends JpaRepository<SecurityEventJpaEntity, UUID> {
}
