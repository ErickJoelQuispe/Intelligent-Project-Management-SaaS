package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.user.domain.exception.ActiveInvitationExistsException;
import com.epm.user.domain.port.in.command.CreateInvitationCommand;
import com.epm.user.domain.port.in.result.InvitationResult;
import com.epm.user.domain.port.out.DomainEventPublisher;
import com.epm.user.domain.port.out.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CreateInvitationUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class CreateInvitationUseCaseImplTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    private CreateInvitationUseCaseImpl useCase;

    private UUID callerId;
    private UUID teamId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        useCase = new CreateInvitationUseCaseImpl(invitationRepository, domainEventPublisher);
        callerId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void createInvitation_happyPath_returnsResultAndSavesAndPublishes() {
        CreateInvitationCommand command = new CreateInvitationCommand(
                teamId, tenantId, "alice@example.com", callerId.toString());

        when(invitationRepository.existsActiveInvitation("alice@example.com", tenantId))
                .thenReturn(false);
        when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InvitationResult result = useCase.createInvitation(callerId, command);

        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.invitationId()).isNotNull();
        assertThat(result.expiresAt()).isNotNull();
        verify(invitationRepository).save(any());
        verify(domainEventPublisher).publish(any());
    }

    // ── Duplicate active invitation → 409 ────────────────────────────────────

    @Test
    void createInvitation_activeInvitationExists_throwsActiveInvitationExistsException() {
        CreateInvitationCommand command = new CreateInvitationCommand(
                teamId, tenantId, "bob@example.com", callerId.toString());

        when(invitationRepository.existsActiveInvitation("bob@example.com", tenantId))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.createInvitation(callerId, command))
                .isInstanceOf(ActiveInvitationExistsException.class);

        verify(invitationRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    // ── Triangulate: different tenant/email combos don't block each other ─────

    @Test
    void createInvitation_differentTenant_noConflict() {
        UUID otherTenantId = UUID.randomUUID();
        CreateInvitationCommand command = new CreateInvitationCommand(
                teamId, otherTenantId, "alice@example.com", callerId.toString());

        when(invitationRepository.existsActiveInvitation("alice@example.com", otherTenantId))
                .thenReturn(false);
        when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InvitationResult result = useCase.createInvitation(callerId, command);

        assertThat(result.email()).isEqualTo("alice@example.com");
        verify(invitationRepository).save(any());
    }
}
