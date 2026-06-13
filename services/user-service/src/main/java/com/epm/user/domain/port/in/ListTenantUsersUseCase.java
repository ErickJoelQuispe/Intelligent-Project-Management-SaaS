package com.epm.user.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.model.UserProfile;

/**
 * Driving port: retrieves a page of active users for a given tenant.
 *
 * <p>DB-level pagination is used — results are never loaded in full and then truncated.
 */
public interface ListTenantUsersUseCase {

    /**
     * Returns a page of active user profiles for the given tenant.
     *
     * @param tenantId the tenant to query
     * @param page     zero-based page number (0 = first page)
     * @param size     maximum number of results per page (capped at MAX_RESULTS)
     * @return list of matching user profiles (never null, may be empty)
     */
    List<UserProfile> listTenantUsers(UUID tenantId, int page, int size);
}
