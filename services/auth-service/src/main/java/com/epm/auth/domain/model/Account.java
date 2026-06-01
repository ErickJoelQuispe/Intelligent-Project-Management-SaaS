package com.epm.auth.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.epm.auth.domain.event.AccountRegisteredEvent;
import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Account aggregate root.
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 * State mutations happen only through business methods.
 * Domain events are accumulated and pulled by the application layer.
 */
public class Account {

    private final UUID id;
    private final UUID tenantId;
    private UUID keycloakUserId;
    private final Email email;
    private AccountStatus status;
    private int failedAttempts;
    private Instant lastLoginAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private int version;
    private Instant deletedAt;

    private final List<Object> domainEvents = new ArrayList<>();

    private Account(
            UUID id,
            UUID tenantId,
            Email email,
            AccountStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.failedAttempts = 0;
        this.version = 0;
    }

    /**
     * Factory method: registers a new account.
     *
     * <p>Generates UUID v7 for {@code id} and {@code tenantId} (time-ordered, different values).
     * Normalizes the email. Sets status to ACTIVE. Records an {@link AccountRegisteredEvent}.
     *
     * @param email     raw email string (will be normalized)
     * @param firstName first name
     * @param lastName  last name
     * @return new Account with domain event recorded
     */
    public static Account register(String email, String firstName, String lastName) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        UUID tenantId = UuidCreator.getTimeOrderedEpoch();
        Instant now = Instant.now();
        Email normalizedEmail = new Email(email);

        Account account = new Account(id, tenantId, normalizedEmail, AccountStatus.ACTIVE, now, now);

        account.domainEvents.add(
                AccountRegisteredEvent.of(id, tenantId, normalizedEmail.value(), firstName, lastName, null));

        return account;
    }

    /**
     * Factory method: reconstitutes an Account from persistent storage.
     *
     * <p>Does NOT register domain events — this is a rehydration, not a new registration.
     * Used exclusively by the persistence adapter.
     *
     * @param id              account UUID
     * @param tenantId        tenant UUID
     * @param keycloakUserId  Keycloak user UUID (may be null)
     * @param email           normalized email string
     * @param status          account status
     * @param failedAttempts  failed login attempts counter
     * @param lastLoginAt     last login timestamp (may be null)
     * @param createdAt       creation timestamp
     * @param updatedAt       last update timestamp
     * @param version         optimistic lock version
     * @param deletedAt       soft-delete timestamp (may be null)
     * @return reconstituted Account
     */
    public static Account reconstitute(
            UUID id,
            UUID tenantId,
            UUID keycloakUserId,
            String email,
            AccountStatus status,
            int failedAttempts,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt,
            int version,
            Instant deletedAt) {
        Account account = new Account(id, tenantId, new Email(email), status, createdAt, updatedAt);
        account.keycloakUserId = keycloakUserId;
        account.failedAttempts = failedAttempts;
        account.lastLoginAt = lastLoginAt;
        account.version = version;
        account.deletedAt = deletedAt;
        return account;
    }

    /** Returns a copy of domain events and clears the internal list. */
    public List<Object> pullDomainEvents() {
        List<Object> events = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return events;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(UUID keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
        this.updatedAt = Instant.now();
    }

    public Email getEmail() {
        return email;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
