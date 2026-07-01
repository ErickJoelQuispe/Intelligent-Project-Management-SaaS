package com.epm.user.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.epm.user.domain.event.InvitationCreatedEvent;
import com.epm.user.domain.exception.InvitationAlreadyUsedException;
import com.epm.user.domain.port.in.command.CreateInvitationCommand;
import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Invitation aggregate root.
 *
 * <p>Represents a pending team invitation. A 128-bit cryptographically-secure
 * token is generated on creation; only the SHA-256 hash is stored in the DB.
 * The plaintext token travels in domain events (for email dispatch) and is
 * accessible via {@link #getPlaintextToken()} immediately after creation.
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public class Invitation {

    private static final int TOKEN_BYTES = 16; // 128 bits
    private static final long EXPIRY_HOURS = 72L;
    private static final String DEFAULT_ROLE = "VIEWER";

    private final UUID id;
    private final UUID teamId;
    private final UUID tenantId;
    private final String email;
    private final String tokenHash;
    private final String role;
    private final Instant expiresAt;
    private Instant usedAt;
    private final String createdBy;
    private final Instant createdAt;
    private final long version;

    /**
     * Transient — only set after {@link #create(CreateInvitationCommand)},
     * never rehydrated from persistence (hash is stored; plaintext is not).
     */
    private final String plaintextToken;

    private final List<Object> domainEvents = new ArrayList<>();

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new invitation.
     *
     * <ul>
     *   <li>Generates a 128-bit {@link SecureRandom} token encoded as Base64 URL.</li>
     *   <li>Stores only the SHA-256 hex hash — plaintext never persisted.</li>
     *   <li>Sets {@code expiresAt = now + 72 hours}.</li>
     *   <li>Records one {@link InvitationCreatedEvent} with the plaintext token.</li>
     * </ul>
     *
     * @param command the invitation creation command
     * @return the new invitation aggregate (call {@link #pullDomainEvents()} to
     *     retrieve the {@link InvitationCreatedEvent} carrying the plaintext token)
     */
    public static Invitation create(CreateInvitationCommand command) {
        String plaintext = generateToken();
        String hash = sha256hex(plaintext);
        Instant now = Instant.now();
        UUID invitationId = UuidCreator.getTimeOrderedEpoch();

        Invitation invitation = new Invitation(
                invitationId,
                command.teamId(),
                command.tenantId(),
                command.email(),
                hash,
                DEFAULT_ROLE,
                now.plus(EXPIRY_HOURS, ChronoUnit.HOURS),
                null,
                command.createdBy(),
                now,
                0L,
                plaintext);

        invitation.domainEvents.add(new InvitationCreatedEvent(
                UuidCreator.getTimeOrderedEpoch(),
                invitationId,
                command.teamId(),
                command.tenantId(),
                command.email(),
                plaintext,
                DEFAULT_ROLE,
                invitation.expiresAt,
                now));

        return invitation;
    }

    /**
     * Reconstitutes an invitation from persistence (no events raised).
     *
     * <p>The plaintext token is NOT available on reconstituted instances.
     */
    public static Invitation reconstitute(
            UUID id, UUID teamId, UUID tenantId, String email,
            String tokenHash, String role,
            Instant expiresAt, Instant usedAt, String createdBy,
            Instant createdAt, long version) {
        return new Invitation(id, teamId, tenantId, email, tokenHash, role,
                expiresAt, usedAt, createdBy, createdAt, version, null);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private Invitation(UUID id, UUID teamId, UUID tenantId, String email,
            String tokenHash, String role,
            Instant expiresAt, Instant usedAt, String createdBy,
            Instant createdAt, long version, String plaintextToken) {
        this.id = id;
        this.teamId = teamId;
        this.tenantId = tenantId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.role = role;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.version = version;
        this.plaintextToken = plaintextToken;
    }

    // ── Business methods ─────────────────────────────────────────────────────

    /**
     * Marks this invitation as used.
     *
     * @throws InvitationAlreadyUsedException if the invitation was already accepted
     */
    public void markUsed() {
        if (this.usedAt != null) {
            throw new InvitationAlreadyUsedException(this.id);
        }
        this.usedAt = Instant.now();
    }

    /**
     * Returns {@code true} if this invitation has passed its expiry time.
     */
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /**
     * Returns all pending domain events and clears the internal list.
     */
    public List<Object> pullDomainEvents() {
        List<Object> copy = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return copy;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getTeamId() { return teamId; }

    public UUID getTenantId() { return tenantId; }

    public String getEmail() { return email; }

    public String getTokenHash() { return tokenHash; }

    public String getRole() { return role; }

    public Instant getExpiresAt() { return expiresAt; }

    public Instant getUsedAt() { return usedAt; }

    public String getCreatedBy() { return createdBy; }

    public Instant getCreatedAt() { return createdAt; }

    public long getVersion() { return version; }

    /**
     * Returns the plaintext token — only available on newly-created invitations.
     * Returns {@code null} for reconstituted instances.
     */
    public String getPlaintextToken() { return plaintextToken; }
}
