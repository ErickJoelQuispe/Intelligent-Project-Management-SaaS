package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.InvitationAlreadyUsedException;
import com.epm.user.domain.exception.InvitationExpiredException;
import com.epm.user.domain.exception.InvitationNotFoundException;
import com.epm.user.domain.model.Invitation;
import com.epm.user.domain.port.out.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ValidateInvitationUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class ValidateInvitationUseCaseImplTest {

    @Mock
    private InvitationRepository invitationRepository;

    private ValidateInvitationUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ValidateInvitationUseCaseImpl(invitationRepository);
    }

    // ── Happy path: valid token returns Invitation ────────────────────────────

    @Test
    void validateInvitation_validToken_returnsInvitation() {
        UUID invId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(72, ChronoUnit.HOURS);
        Invitation invitation = Invitation.reconstitute(
                invId, teamId, tenantId, "alice@example.com",
                "somehash", "VIEWER", expiresAt, null, "system", Instant.now(), 0L);

        // The use case hashes the plaintext and looks up by hash
        // We capture what hash it produces by matching any string
        when(invitationRepository.findByTokenHash(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(invitation));

        Invitation result = useCase.validateInvitation("plaintext-token");

        assertThat(result.getId()).isEqualTo(invId);
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getUsedAt()).isNull();
    }

    // ── Token not found → 404 ─────────────────────────────────────────────────

    @Test
    void validateInvitation_tokenNotFound_throwsInvitationNotFoundException() {
        when(invitationRepository.findByTokenHash(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.validateInvitation("unknown-token"))
                .isInstanceOf(InvitationNotFoundException.class)
                .hasMessageContaining("not found");
    }

    // ── Invitation expired → 410 ──────────────────────────────────────────────

    @Test
    void validateInvitation_expired_throwsInvitationExpiredException() {
        UUID invId = UUID.randomUUID();
        Instant expiredAt = Instant.now().minus(1, ChronoUnit.HOURS);
        Invitation expired = Invitation.reconstitute(
                invId, UUID.randomUUID(), UUID.randomUUID(), "bob@example.com",
                "hash", "VIEWER", expiredAt, null, "system", Instant.now().minus(2, ChronoUnit.DAYS), 0L);

        when(invitationRepository.findByTokenHash(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> useCase.validateInvitation("expired-token"))
                .isInstanceOf(InvitationExpiredException.class);
    }

    // ── Invitation already used → 409 ─────────────────────────────────────────

    @Test
    void validateInvitation_alreadyUsed_throwsInvitationAlreadyUsedException() {
        UUID invId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(72, ChronoUnit.HOURS);
        Invitation used = Invitation.reconstitute(
                invId, UUID.randomUUID(), UUID.randomUUID(), "carol@example.com",
                "hash", "VIEWER", expiresAt, Instant.now().minus(1, ChronoUnit.HOURS), "system", Instant.now().minus(2, ChronoUnit.DAYS), 0L);

        when(invitationRepository.findByTokenHash(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(used));

        assertThatThrownBy(() -> useCase.validateInvitation("used-token"))
                .isInstanceOf(InvitationAlreadyUsedException.class);
    }
}
