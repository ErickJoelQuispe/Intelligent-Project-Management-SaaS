package com.epm.notification.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import com.epm.notification.domain.model.UserEmailCache;

/**
 * Output port for user email cache persistence.
 *
 * <p>Implemented by the infrastructure layer. Save is upsert-idempotent:
 * saving the same userId twice results in one row with updated values.
 */
public interface UserEmailCacheRepository {

    /**
     * Returns the cached email for the given user, if present.
     *
     * @param userId the user to look up
     * @return an Optional containing the cached entry, or empty if not found
     */
    Optional<UserEmailCache> findByUserId(UUID userId);

    /**
     * Persists or updates the email cache entry for the given user.
     * This is an upsert: if a row for userId already exists it is overwritten.
     *
     * @param userEmailCache the entry to save
     */
    void save(UserEmailCache userEmailCache);
}
