package com.epm.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.auth.domain.exception.InvitationTokenAlreadyUsedException;
import com.epm.auth.domain.exception.InvitationTokenExpiredException;
import com.epm.auth.domain.exception.InvitationTokenInvalidException;
import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.port.in.command.AcceptInvitationCommand;
import com.epm.auth.domain.port.out.AccountRegistrationTransaction;
import com.epm.auth.domain.port.out.AccountRepository;
import com.epm.auth.domain.port.out.IdentityProviderPort;
import com.epm.auth.domain.port.out.InvitationValidationPort;
import com.epm.auth.domain.port.out.InvitationValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AcceptInvitationUseCaseImpl}.
 *
 * <p>Strict TDD: tests written before implementation.
 * Uses Mockito to stub driven ports. No Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class AcceptInvitationUseCaseImplTest {

    @Mock
    private InvitationValidationPort invitationValidationPort;

    @Mock
    private IdentityProviderPort identityProvider;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountRegistrationTransaction registrationTransaction;

    private AcceptInvitationUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new AcceptInvitationUseCaseImpl(
                invitationValidationPort, identityProvider, accountRepository, registrationTransaction);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void happyPathCreatesKeycloakUserWithInvitingTenantIdAndViewerRole() {
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID keycloakUserId = UUID.randomUUID();

        InvitationValidationResult result = new InvitationValidationResult(
                invitationId, "invited@example.com", tenantId, teamId, "VIEWER");

        when(invitationValidationPort.validateToken("token-abc")).thenReturn(result);
        when(identityProvider.createUser(
                eq("invited@example.com"),
                eq("password123"),
                eq("John"),
                eq("Doe"),
                eq(tenantId)))
                .thenReturn(keycloakUserId);
        when(registrationTransaction.saveAndPublish(any(Account.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AcceptInvitationCommand command =
                new AcceptInvitationCommand("token-abc", "John", "Doe", "password123");
        useCase.accept(command);

        // tenantId MUST come from invitation result — not generated
        verify(identityProvider).createUser("invited@example.com", "password123", "John", "Doe", tenantId);
        // Role MUST be VIEWER — not ADMIN
        verify(identityProvider).assignRole(keycloakUserId, "VIEWER");
    }

    @Test
    void happyPathMarksInvitationUsedAfterKeycloakCreation() {
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID keycloakUserId = UUID.randomUUID();

        InvitationValidationResult result = new InvitationValidationResult(
                invitationId, "invited@example.com", tenantId, teamId, "VIEWER");

        when(invitationValidationPort.validateToken(any())).thenReturn(result);
        when(identityProvider.createUser(any(), any(), any(), any(), any())).thenReturn(keycloakUserId);
        when(registrationTransaction.saveAndPublish(any(Account.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.accept(new AcceptInvitationCommand("token-abc", "John", "Doe", "password123"));

        verify(invitationValidationPort).markUsed(invitationId);
    }

    @Test
    void happyPathPersistsAndPublishesEvent() {
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID keycloakUserId = UUID.randomUUID();

        InvitationValidationResult result = new InvitationValidationResult(
                invitationId, "invited@example.com", tenantId, teamId, "VIEWER");

        when(invitationValidationPort.validateToken(any())).thenReturn(result);
        when(identityProvider.createUser(any(), any(), any(), any(), any())).thenReturn(keycloakUserId);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(registrationTransaction.saveAndPublish(accountCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.accept(new AcceptInvitationCommand("token-abc", "John", "Doe", "password123"));

        verify(registrationTransaction).saveAndPublish(any(Account.class));
        Account saved = accountCaptor.getValue();
        // The account must use the inviting tenantId
        org.assertj.core.api.Assertions.assertThat(saved.getTenantId()).isEqualTo(tenantId);
        org.assertj.core.api.Assertions.assertThat(saved.getKeycloakUserId()).isEqualTo(keycloakUserId);
    }

    // ── Token invalid (404) propagated ───────────────────────────────────────

    @Test
    void tokenNotFoundPropagatesInvalidException() {
        when(invitationValidationPort.validateToken(any()))
                .thenThrow(new InvitationTokenInvalidException("Token not found"));

        assertThatThrownBy(() -> useCase.accept(
                new AcceptInvitationCommand("bad-token", "John", "Doe", "password123")))
                .isInstanceOf(InvitationTokenInvalidException.class);

        verify(identityProvider, never()).createUser(any(), any(), any(), any(), any());
    }

    // ── Token expired (410) propagated ───────────────────────────────────────

    @Test
    void tokenExpiredPropagatesExpiredException() {
        when(invitationValidationPort.validateToken(any()))
                .thenThrow(new InvitationTokenExpiredException("Token expired"));

        assertThatThrownBy(() -> useCase.accept(
                new AcceptInvitationCommand("expired-token", "John", "Doe", "password123")))
                .isInstanceOf(InvitationTokenExpiredException.class);

        verify(identityProvider, never()).createUser(any(), any(), any(), any(), any());
    }

    // ── Token already used (409) propagated ──────────────────────────────────

    @Test
    void tokenAlreadyUsedPropagatesAlreadyUsedException() {
        when(invitationValidationPort.validateToken(any()))
                .thenThrow(new InvitationTokenAlreadyUsedException("Already used"));

        assertThatThrownBy(() -> useCase.accept(
                new AcceptInvitationCommand("used-token", "John", "Doe", "password123")))
                .isInstanceOf(InvitationTokenAlreadyUsedException.class);

        verify(identityProvider, never()).createUser(any(), any(), any(), any(), any());
    }

    // ── Keycloak failure triggers compensation ────────────────────────────────

    @Test
    void keycloakAssignRoleFailureTriggersUserDeletion() {
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID keycloakUserId = UUID.randomUUID();

        InvitationValidationResult result = new InvitationValidationResult(
                invitationId, "invited@example.com", tenantId, teamId, "VIEWER");

        when(invitationValidationPort.validateToken(any())).thenReturn(result);
        when(identityProvider.createUser(any(), any(), any(), any(), any())).thenReturn(keycloakUserId);
        doThrow(new com.epm.auth.domain.exception.IdentityProviderException("Role assignment failed", 0))
                .when(identityProvider).assignRole(any(UUID.class), any(String.class));

        assertThatThrownBy(() -> useCase.accept(
                new AcceptInvitationCommand("token-abc", "John", "Doe", "password123")))
                .isInstanceOf(com.epm.auth.domain.exception.IdentityProviderException.class);

        // Compensation: delete orphaned Keycloak user
        verify(identityProvider).deleteUser(keycloakUserId);
        // markUsed must NOT be called on failure
        verify(invitationValidationPort, never()).markUsed(any());
    }
}
