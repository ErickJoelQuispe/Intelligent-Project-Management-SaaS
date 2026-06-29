package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.InvitationAlreadyUsedException;
import com.epm.user.domain.exception.InvitationNotFoundException;
import com.epm.user.domain.model.Invitation;
import com.epm.user.domain.port.out.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link MarkInvitationUsedUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class MarkInvitationUsedUseCaseImplTest {

    @Mock
    private InvitationRepository invitationRepository;

    private MarkInvitationUsedUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new MarkInvitationUsedUseCaseImpl(invitationRepository);
    }

    // ── Happy path: marks invitation as used and saves ────────────────────────

    @Test
    void markInvitationUsed_validId_savesWithUsedAt() {
        UUID invId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(72, ChronoUnit.HOURS);
        Invitation invitation = Invitation.reconstitute(
                invId, UUID.randomUUID(), UUID.randomUUID(), "alice@example.com",
                "hash", "VIEWER", expiresAt, null, "system", Instant.now(), 0L);

        when(invitationRepository.findById(invId)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.markInvitationUsed(invId);

        verify(invitationRepository).save(any());
    }

    // ── Not found → throws InvitationNotFoundException ────────────────────────

    @Test
    void markInvitationUsed_notFound_throwsInvitationNotFoundException() {
        UUID invId = UUID.randomUUID();
        when(invitationRepository.findById(invId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.markInvitationUsed(invId))
                .isInstanceOf(InvitationNotFoundException.class)
                .hasMessageContaining(invId.toString());
    }

    // ── Already used → throws InvitationAlreadyUsedException ─────────────────

    @Test
    void markInvitationUsed_alreadyUsed_throwsInvitationAlreadyUsedException() {
        UUID invId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(72, ChronoUnit.HOURS);
        Invitation used = Invitation.reconstitute(
                invId, UUID.randomUUID(), UUID.randomUUID(), "bob@example.com",
                "hash", "VIEWER", expiresAt,
                Instant.now().minus(1, ChronoUnit.HOURS), // usedAt is set
                "system", Instant.now().minus(2, ChronoUnit.DAYS), 0L);

        when(invitationRepository.findById(invId)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> useCase.markInvitationUsed(invId))
                .isInstanceOf(InvitationAlreadyUsedException.class);
    }
}
