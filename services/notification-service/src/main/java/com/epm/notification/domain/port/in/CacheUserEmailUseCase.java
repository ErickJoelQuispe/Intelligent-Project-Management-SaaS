package com.epm.notification.domain.port.in;

import java.util.UUID;

/**
 * In port for caching a user's email address locally.
 *
 * <p>Called when a {@code UserRegistered} event is consumed from {@code user.events}.
 * Populates the {@code user_email_cache} table to avoid synchronous coupling
 * to the user-service during email dispatch.
 */
public interface CacheUserEmailUseCase {

    /**
     * Caches or updates the email for a given user.
     *
     * @param userId   the user whose email to cache
     * @param tenantId the tenant the user belongs to
     * @param email    the user's email address
     */
    void cacheUserEmail(UUID userId, UUID tenantId, String email);
}
