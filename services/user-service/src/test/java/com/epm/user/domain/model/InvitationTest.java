package com.epm.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.epm.user.domain.event.InvitationCreatedEvent;
import com.epm.user.domain.exception.InvitationAlreadyUsedException;
import com.epm.user.domain.port.in.command.CreateInvitationCommand;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Invitation} aggregate root.
 *
 * <p>Tests are written FIRST (TDD RED) before the production class exists.
 */
class InvitationTest {

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String EMAIL = "invited@example.com";
    private static final String CREATED_BY = "admin@example.com";

    private CreateInvitationCommand defaultCommand() {
        return new CreateInvitationCommand(TEAM_ID, TENANT_ID, EMAIL, CREATED_BY);
    }

    // ── create() — token and tokenHash ───────────────────────────────────────

    @Test
    void create_generatesNonNullToken() {
        Invitation invitation = Invitation.create(defaultCommand());
        assertThat(invitation.getPlaintextToken()).isNotNull().isNotBlank();
    }

    @Test
    void create_tokenIsBase64UrlEncoded() {
        Invitation invitation = Invitation.create(defaultCommand());
        // base64url characters: A-Z, a-z, 0-9, -, _  (no padding or +/)
        String token = invitation.getPlaintextToken();
        assertThat(token).matches("[A-Za-z0-9+/=_-]+");
    }

    @Test
    void create_tokenHashIsSha256OfPlaintextToken() throws NoSuchAlgorithmException {
        Invitation invitation = Invitation.create(defaultCommand());
        String expectedHash = sha256hex(invitation.getPlaintextToken());
        assertThat(invitation.getTokenHash()).isEqualTo(expectedHash);
    }

    @Test
    void create_twoInvitationsProduceDifferentTokens() {
        Invitation first = Invitation.create(defaultCommand());
        Invitation second = Invitation.create(defaultCommand());
        assertThat(first.getPlaintextToken()).isNotEqualTo(second.getPlaintextToken());
    }

    // ── create() — expiresAt ─────────────────────────────────────────────────

    @Test
    void create_expiresAtIsApproximately72HoursFromNow() {
        Instant before = Instant.now();
        Invitation invitation = Invitation.create(defaultCommand());
        Instant after = Instant.now();

        Instant expectedMin = before.plus(72, ChronoUnit.HOURS);
        Instant expectedMax = after.plus(72, ChronoUnit.HOURS);

        assertThat(invitation.getExpiresAt())
                .isAfterOrEqualTo(expectedMin)
                .isBeforeOrEqualTo(expectedMax);
    }

    // ── create() — fields ────────────────────────────────────────────────────

    @Test
    void create_setsRoleToViewer() {
        Invitation invitation = Invitation.create(defaultCommand());
        assertThat(invitation.getRole()).isEqualTo("VIEWER");
    }

    @Test
    void create_setsEmailTeamIdTenantIdCreatedBy() {
        Invitation invitation = Invitation.create(defaultCommand());
        assertThat(invitation.getEmail()).isEqualTo(EMAIL);
        assertThat(invitation.getTeamId()).isEqualTo(TEAM_ID);
        assertThat(invitation.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(invitation.getCreatedBy()).isEqualTo(CREATED_BY);
    }

    @Test
    void create_setsNonNullId() {
        Invitation invitation = Invitation.create(defaultCommand());
        assertThat(invitation.getId()).isNotNull();
    }

    @Test
    void create_usedAtIsNullOnCreation() {
        Invitation invitation = Invitation.create(defaultCommand());
        assertThat(invitation.getUsedAt()).isNull();
    }

    // ── create() — domain event ───────────────────────────────────────────────

    @Test
    void create_recordsExactlyOneInvitationCreatedEvent() {
        Invitation invitation = Invitation.create(defaultCommand());
        List<Object> events = invitation.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(InvitationCreatedEvent.class);
    }

    @Test
    void create_eventContainsPlaintextToken() {
        Invitation invitation = Invitation.create(defaultCommand());
        String plaintext = invitation.getPlaintextToken();
        List<Object> events = invitation.pullDomainEvents();
        InvitationCreatedEvent event = (InvitationCreatedEvent) events.get(0);
        assertThat(event.token()).isEqualTo(plaintext);
    }

    @Test
    void create_eventContainsCorrectEmailAndIds() {
        Invitation invitation = Invitation.create(defaultCommand());
        List<Object> events = invitation.pullDomainEvents();
        InvitationCreatedEvent event = (InvitationCreatedEvent) events.get(0);
        assertThat(event.email()).isEqualTo(EMAIL);
        assertThat(event.teamId()).isEqualTo(TEAM_ID);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.invitationId()).isEqualTo(invitation.getId());
    }

    // ── markUsed() ───────────────────────────────────────────────────────────

    @Test
    void markUsed_setsUsedAt() {
        Invitation invitation = Invitation.create(defaultCommand());
        invitation.pullDomainEvents();

        Instant before = Instant.now();
        invitation.markUsed();
        Instant after = Instant.now();

        assertThat(invitation.getUsedAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    void markUsed_secondCallThrowsInvitationAlreadyUsedException() {
        Invitation invitation = Invitation.create(defaultCommand());
        invitation.markUsed();

        assertThatThrownBy(invitation::markUsed)
                .isInstanceOf(InvitationAlreadyUsedException.class);
    }

    // ── isExpired() ──────────────────────────────────────────────────────────

    @Test
    void isExpired_returnsFalseForFreshInvitation() {
        Invitation invitation = Invitation.create(defaultCommand());
        assertThat(invitation.isExpired()).isFalse();
    }

    @Test
    void isExpired_returnsTrueWhenExpiresAtIsInThePast() {
        // Use package-private reconstitute to set expiresAt in the past
        Invitation expired = Invitation.reconstitute(
                UUID.randomUUID(), TEAM_ID, TENANT_ID, EMAIL,
                "hash", "VIEWER",
                Instant.now().minus(1, ChronoUnit.HOURS), // already expired
                null, CREATED_BY, Instant.now(), 0L);

        assertThat(expired.isExpired()).isTrue();
    }

    // ── pullDomainEvents() — clears after first call ──────────────────────────

    @Test
    void pullDomainEvents_clearsAfterFirstCall() {
        Invitation invitation = Invitation.create(defaultCommand());
        List<Object> first = invitation.pullDomainEvents();
        List<Object> second = invitation.pullDomainEvents();

        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String sha256hex(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }
}
