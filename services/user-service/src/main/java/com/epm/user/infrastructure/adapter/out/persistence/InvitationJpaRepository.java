package com.epm.user.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvitationJpaEntity}.
 */
public interface InvitationJpaRepository extends JpaRepository<InvitationJpaEntity, UUID> {

    /**
     * Finds an invitation by its SHA-256 token hash.
     *
     * @param tokenHash the hex-encoded SHA-256 hash of the plaintext token
     * @return the invitation if found
     */
    Optional<InvitationJpaEntity> findByTokenHash(String tokenHash);

    /**
     * Returns {@code true} if an active (not yet used) invitation exists
     * for the given tenant and email.
     *
     * <p>An invitation is considered active when {@code used_at IS NULL}
     * (expiry is checked at the use-case level, not at the DB level, to
     * keep the query simple and the domain rules in one place).
     *
     * @param tenantId the tenant
     * @param email    the invitee's email address
     * @return {@code true} if a matching unused invitation exists
     */
    boolean existsByTenantIdAndEmailAndUsedAtIsNull(UUID tenantId, String email);
}
