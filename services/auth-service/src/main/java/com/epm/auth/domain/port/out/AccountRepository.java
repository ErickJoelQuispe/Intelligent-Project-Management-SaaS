package com.epm.auth.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.model.Email;

/**
 * Driven port: persistence contract for {@link Account} aggregate.
 *
 * <p>Implemented by the infrastructure layer (JPA adapter).
 */
public interface AccountRepository {

    /**
     * Returns true if a non-deleted account exists for the given email within any tenant.
     *
     * @param email normalized email value object
     * @return true if duplicate exists
     */
    boolean existsByEmail(Email email);

    /**
     * Persists the account (insert or update).
     *
     * @param account the account to persist
     * @return the saved account (may have updated fields)
     */
    Account save(Account account);

    /**
     * Finds an account by its UUID.
     *
     * @param id account UUID
     * @return optional containing the account if found
     */
    Optional<Account> findById(UUID id);
}
