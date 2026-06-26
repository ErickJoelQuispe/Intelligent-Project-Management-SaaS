package com.epm.user.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.UserProfile;

/**
 * Driven port: persistence for {@link UserProfile} aggregates.
 */
public interface UserProfileRepository {

    Optional<UserProfile> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Finds an active (non-deleted) user profile by its ID, regardless of tenant.
     *
     * <p>Used in account deletion where the authenticated user's ID is the JWT {@code sub} claim
     * — tenant scoping is not needed since the user can only delete their own account.
     *
     * @param id the user/account UUID
     * @return the user profile if active, or empty if not found or already deleted
     */
    Optional<UserProfile> findById(UUID id);

    List<UserProfile> findAllByTenantId(UUID tenantId);

    /**
     * Returns a page of active user profiles for the given tenant.
     * DB-level limiting — no in-memory truncation.
     *
     * @param tenantId the tenant to query
     * @param page     zero-based page number
     * @param size     maximum number of results (capped by the caller)
     * @return matching profiles for the requested page
     */
    List<UserProfile> findPageByTenantId(UUID tenantId, int page, int size);

    UserProfile save(UserProfile profile);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
