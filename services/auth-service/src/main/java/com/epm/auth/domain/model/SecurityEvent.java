package com.epm.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * SecurityEvent entity — immutable audit record of security-relevant actions.
 *
 * <p>Pure Java record — no Spring, no JPA annotations.
 * Immutable by design: no setters, no soft-delete field.
 */
public record SecurityEvent(
        UUID id,
        UUID tenantId,
        UUID accountId,
        String eventType,
        String ipAddress,
        String userAgent,
        Instant occurredAt) {

    /**
     * Creates a LOGOUT security event.
     *
     * @param tenantId   tenant UUID
     * @param accountId  account UUID
     * @param ipAddress  client IP address
     * @param userAgent  client User-Agent header
     * @return immutable SecurityEvent with type LOGOUT
     */
    public static SecurityEvent logout(UUID tenantId, UUID accountId, String ipAddress, String userAgent) {
        return new SecurityEvent(
                UuidCreator.getTimeOrderedEpoch(),
                tenantId,
                accountId,
                "LOGOUT",
                ipAddress,
                userAgent,
                Instant.now());
    }
}
