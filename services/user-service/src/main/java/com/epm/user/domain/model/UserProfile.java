package com.epm.user.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.epm.user.domain.event.ProfileUpdated;
import com.epm.user.domain.exception.OptimisticLockException;
import com.github.f4b6a3.uuid.UuidCreator;

/**
 * UserProfile aggregate root.
 *
 * <p>Represents the profile of a registered user. The {@code id} mirrors the
 * {@code accountId} from auth-service (same UUID). Email is read-only after creation.
 * Uses optimistic locking via {@code version} field.
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public class UserProfile {

    private final UUID id;
    private final UUID tenantId;
    private final String email; // read-only after creation
    private String firstName;
    private String lastName;
    private String bio;
    private String avatarUrl;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private final List<Object> domainEvents = new ArrayList<>();

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new UserProfile for a registered account.
     *
     * @param accountId  the auth-service accountId (mirrors the Keycloak sub)
     * @param tenantId   the tenant this profile belongs to
     * @param email      the user's email address (normalized)
     * @param firstName  the user's first name
     * @param lastName   the user's last name
     * @return a new UserProfile with version 0
     */
    public static UserProfile create(UUID accountId, UUID tenantId, String email,
            String firstName, String lastName) {
        Email emailVo = new Email(email);
        Instant now = Instant.now();
        return new UserProfile(accountId, tenantId, emailVo.value(), firstName, lastName,
                null, null, 0L, now, now, null);
    }

    /**
     * Reconstitutes a UserProfile from persistence (no events raised).
     */
    public static UserProfile reconstitute(UUID id, UUID tenantId, String email,
            String firstName, String lastName, String bio, String avatarUrl,
            long version, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        return new UserProfile(id, tenantId, email, firstName, lastName, bio, avatarUrl,
                version, createdAt, updatedAt, deletedAt);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private UserProfile(UUID id, UUID tenantId, String email,
            String firstName, String lastName, String bio, String avatarUrl,
            long version, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    // ── Business methods ─────────────────────────────────────────────────────

    /**
     * Updates mutable profile fields.
     *
     * @param firstName       new first name (null = no change)
     * @param lastName        new last name (null = no change)
     * @param bio             new bio (null = clear)
     * @param avatarUrl       new avatar URL (null = clear)
     * @param expectedVersion must match current version (optimistic locking)
     * @throws OptimisticLockException if version mismatch detected
     */
    public void update(String firstName, String lastName, String bio, String avatarUrl, long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new OptimisticLockException("version mismatch: expected " + expectedVersion
                    + " but was " + this.version);
        }
        if (firstName != null) {
            this.firstName = firstName;
        }
        if (lastName != null) {
            this.lastName = lastName;
        }
        this.bio = bio;
        this.avatarUrl = avatarUrl;
        this.version++;
        this.updatedAt = Instant.now();

        domainEvents.add(new ProfileUpdated(
                UuidCreator.getTimeOrderedEpoch(),
                this.id,
                this.tenantId,
                this.firstName,
                this.lastName,
                this.bio,
                this.avatarUrl,
                Instant.now()));
    }

    /**
     * Returns all pending domain events and clears the internal list.
     */
    public List<Object> pullDomainEvents() {
        List<Object> copy = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return copy;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }

    public String getEmail() { return email; }

    public String getFirstName() { return firstName; }

    public String getLastName() { return lastName; }

    public String getBio() { return bio; }

    public String getAvatarUrl() { return avatarUrl; }

    public long getVersion() { return version; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
}
