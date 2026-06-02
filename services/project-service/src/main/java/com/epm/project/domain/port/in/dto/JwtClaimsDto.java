package com.epm.project.domain.port.in.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTO carrying claims extracted from a JWT token.
 */
public record JwtClaimsDto(
        UUID profileId,
        UUID tenantId,
        String email,
        List<String> roles) {
}
