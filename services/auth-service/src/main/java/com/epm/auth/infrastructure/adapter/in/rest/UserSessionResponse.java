package com.epm.auth.infrastructure.adapter.in.rest;

/**
 * Response DTO representing a single Keycloak user session.
 *
 * <p>Timestamps are serialized as ISO-8601 strings (UTC).
 *
 * @param sessionId   the Keycloak session identifier
 * @param ipAddress   the client IP address recorded by Keycloak
 * @param started     session start time as ISO-8601 string
 * @param lastAccess  last activity time as ISO-8601 string
 */
public record UserSessionResponse(
        String sessionId,
        String ipAddress,
        String started,
        String lastAccess
) {}
