package com.epm.auth.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link AccountJpaEntity}.
 */
public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {

    /**
     * Returns true if an account with the given email exists and has not been soft-deleted.
     *
     * @param email normalized email string
     * @return true if a non-deleted account exists with that email
     */
    boolean existsByEmailAndDeletedAtIsNull(String email);
}
