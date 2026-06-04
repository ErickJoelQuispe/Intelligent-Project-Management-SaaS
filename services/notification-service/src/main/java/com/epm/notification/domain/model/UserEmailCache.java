package com.epm.notification.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain model representing a cached user email address.
 *
 * <p>Populated by consuming {@code UserRegistered} events from {@code user.events}.
 * Avoids synchronous coupling to user-service for email resolution.
 *
 * <p>Pure Java — no Spring or JPA annotations.
 */
public class UserEmailCache {

    private final UUID userId;
    private final UUID tenantId;
    private final String email;
    private final LocalDateTime updatedAt;

    public UserEmailCache(UUID userId, UUID tenantId, String email, LocalDateTime updatedAt) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() { return userId; }

    public UUID getTenantId() { return tenantId; }

    public String getEmail() { return email; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
