package com.epm.user.infrastructure.adapter.in.rest;

import java.util.UUID;

import com.epm.user.domain.port.in.dto.JwtClaimsDto;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Extracts claims from a Spring Security {@link Jwt} into domain DTOs.
 */
@Component
public class JwtClaimsExtractor {

    /**
     * Returns the user's ID from the JWT subject.
     */
    public UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /**
     * Returns the tenant ID from the {@code tenant_id} JWT claim.
     */
    public UUID getTenantId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("tenant_id"));
    }

    /**
     * Converts JWT claims to a {@link JwtClaimsDto} for provisional profile construction.
     */
    public JwtClaimsDto toClaims(Jwt jwt) {
        return new JwtClaimsDto(
                getUserId(jwt),
                getTenantId(jwt),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"));
    }
}
