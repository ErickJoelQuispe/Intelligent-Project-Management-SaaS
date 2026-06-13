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
