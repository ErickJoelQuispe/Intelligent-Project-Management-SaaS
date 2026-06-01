package com.epm.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.auth.domain.exception.DuplicateEmailException;
import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.model.Email;
import com.epm.auth.domain.port.in.command.RegisterAccountCommand;
import com.epm.auth.domain.port.in.result.RegisterAccountResult;
import com.epm.auth.domain.port.out.AccountRepository;
import com.epm.auth.domain.port.out.DomainEventPublisher;
import com.epm.auth.domain.port.out.IdentityProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RegisterAccountUseCaseImpl}.
 *
 * <p>Uses Mockito to stub driven ports. No Spring context needed.
 * Tests run RED first — RegisterAccountUseCaseImpl does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class RegisterAccountUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private IdentityProviderPort identityProvider;

    @Mock
    private DomainEventPublisher eventPublisher;

    private RegisterAccountUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterAccountUseCaseImpl(accountRepository, identityProvider, eventPublisher);
    }

    @Test
    void happyPathReturnsResultWithAccountAndKeycloakIds() {
        UUID keycloakUserId = UUID.randomUUID();
        RegisterAccountCommand command =
                new RegisterAccountCommand("alice@example.com", "secret123", "Alice", "Smith");

        when(accountRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(identityProvider.createUser(
                        eq("alice@example.com"),
                        eq("secret123"),
                        eq("Alice"),
                        eq("Smith"),
                        any(UUID.class)))
                .thenReturn(keycloakUserId);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterAccountResult result = useCase.register(command);

        assertThat(result.accountId()).isNotNull();
        assertThat(result.keycloakUserId()).isEqualTo(keycloakUserId);
        assertThat(result.email()).isEqualTo("alice@example.com");
    }

    @Test
    void duplicateEmailThrowsDuplicateEmailException() {
        RegisterAccountCommand command =
                new RegisterAccountCommand("alice@example.com", "secret123", "Alice", "Smith");
        when(accountRepository.existsByEmail(new Email("alice@example.com"))).thenReturn(true);

        assertThatThrownBy(() -> useCase.register(command))
                .isInstanceOf(DuplicateEmailException.class);

        verify(identityProvider, never()).createUser(any(), any(), any(), any(), any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void identityProviderFailureThrowsIdentityProviderException() {
        RegisterAccountCommand command =
                new RegisterAccountCommand("alice@example.com", "secret123", "Alice", "Smith");
        when(accountRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(identityProvider.createUser(any(), any(), any(), any(), any()))
                .thenThrow(new IdentityProviderException("Keycloak unavailable", 30));

        assertThatThrownBy(() -> useCase.register(command))
                .isInstanceOf(IdentityProviderException.class)
                .hasMessageContaining("Keycloak unavailable");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void accountRepositorySaveIsCalledOnce() {
        UUID keycloakUserId = UUID.randomUUID();
        RegisterAccountCommand command =
                new RegisterAccountCommand("alice@example.com", "secret123", "Alice", "Smith");

        when(accountRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(identityProvider.createUser(any(), any(), any(), any(), any())).thenReturn(keycloakUserId);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.register(command);

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void identityProviderCreateUserIsCalledOnce() {
        UUID keycloakUserId = UUID.randomUUID();
        RegisterAccountCommand command =
                new RegisterAccountCommand("bob@example.com", "password123", "Bob", "Jones");

        when(accountRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(identityProvider.createUser(any(), any(), any(), any(), any())).thenReturn(keycloakUserId);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.register(command);

        verify(identityProvider).createUser(eq("bob@example.com"), eq("password123"), eq("Bob"), eq("Jones"), any(UUID.class));
    }

    @Test
    void domainEventPublisherIsCalledAfterSave() {
        UUID keycloakUserId = UUID.randomUUID();
        RegisterAccountCommand command =
                new RegisterAccountCommand("alice@example.com", "secret123", "Alice", "Smith");

        when(accountRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(identityProvider.createUser(any(), any(), any(), any(), any())).thenReturn(keycloakUserId);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.register(command);

        ArgumentCaptor<java.util.List> eventsCaptor = ArgumentCaptor.forClass(java.util.List.class);
        verify(eventPublisher).publish(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).hasSize(1);
    }
}
