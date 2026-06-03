package com.epm.task.infrastructure.adapter.in.web;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Extracts claims from a Spring Security {@link Jwt} into usable values.
 */
@Component
public class JwtClaimsExtractor {

    public UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public UUID getTenantId(Jwt jwt) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return UUID.fromString(tenantId);
    }
}
