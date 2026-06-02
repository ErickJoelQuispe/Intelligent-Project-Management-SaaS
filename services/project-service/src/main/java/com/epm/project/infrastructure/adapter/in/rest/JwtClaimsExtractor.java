package com.epm.project.infrastructure.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import com.epm.project.domain.port.in.dto.JwtClaimsDto;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Extracts claims from a Spring Security {@link Jwt} into domain DTOs.
 *
 * <p>NOTE: must be explicitly {@code @Import}ed in every {@code @WebMvcTest} — it is
 * NOT auto-discovered by the controller slice.
 */
@Component
public class JwtClaimsExtractor {

    public UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public UUID getTenantId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("tenant_id"));
    }

    public JwtClaimsDto toClaims(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new JwtClaimsDto(
                getUserId(jwt),
                getTenantId(jwt),
                jwt.getClaimAsString("email"),
                roles != null ? roles : List.of());
    }
}
