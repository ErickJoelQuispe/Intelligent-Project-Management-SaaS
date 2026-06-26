package com.epm.auth.domain.model;

import java.time.Instant;

/**
 * Domain record representing an active Keycloak user session.
 *
 * <p>Mapped from {@code UserSessionRepresentation} returned by the Keycloak Admin API.
 * All timestamps are in UTC.
 */
public record UserSession(
        String sessionId,
        String ipAddress,
        Instant started,
        Instant lastAccess
) {}
