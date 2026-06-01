package com.epm.user.domain.port.in.dto;

import java.util.UUID;

/**
 * Carries JWT claims for provisional profile construction (D3).
 *
 * <p>When no UserProfile exists in the database, the use case builds a provisional
 * result from these claims. This avoids 404 responses and frontend retry logic.
 */
public record JwtClaimsDto(
        UUID userId,
        UUID tenantId,
        String email,
        String firstName,
        String lastName) {
}
