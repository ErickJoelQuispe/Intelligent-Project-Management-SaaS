package com.epm.user.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.model.UserProfile;

/**
 * Driving port: retrieves all active users for a given tenant.
 *
 * <p>Results are capped at 100 — future work: add cursor-based pagination.
 */
public interface ListTenantUsersUseCase {

    /**
     * Returns up to 100 active user profiles for the given tenant.
     *
     * @param tenantId the tenant to query
     * @return list of matching user profiles (never null, may be empty)
     */
    List<UserProfile> listTenantUsers(UUID tenantId);
}
