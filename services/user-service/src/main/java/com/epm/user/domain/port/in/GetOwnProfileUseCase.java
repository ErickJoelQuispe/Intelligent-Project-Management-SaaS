package com.epm.user.domain.port.in;

import java.util.UUID;

import com.epm.user.domain.port.in.dto.JwtClaimsDto;
import com.epm.user.domain.port.in.result.UserProfileResult;

/**
 * Driving port: retrieves the calling user's profile.
 *
 * <p>If the profile is not found in the database, returns a provisional profile
 * built from JWT claims (D3 — zero frontend retry logic).
 */
public interface GetOwnProfileUseCase {

    /**
     * Returns the profile for the given user within the tenant.
     *
     * @param userId    the authenticated user's ID
     * @param tenantId  the authenticated user's tenant
     * @param jwtClaims JWT claims for provisional profile fallback
     * @return the user's profile result (never null)
     */
    UserProfileResult getProfile(UUID userId, UUID tenantId, JwtClaimsDto jwtClaims);
}
