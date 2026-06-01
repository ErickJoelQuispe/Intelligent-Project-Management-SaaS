package com.epm.auth.application.usecase;

import java.util.UUID;

import com.epm.auth.domain.exception.DuplicateEmailException;
import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.model.Email;
import com.epm.auth.domain.port.in.RegisterAccountUseCase;
import com.epm.auth.domain.port.in.command.RegisterAccountCommand;
import com.epm.auth.domain.port.in.result.RegisterAccountResult;
import com.epm.auth.domain.port.out.AccountRepository;
import com.epm.auth.domain.port.out.DomainEventPublisher;
import com.epm.auth.domain.port.out.IdentityProviderPort;

/**
 * Implementation of {@link RegisterAccountUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 * Orchestrates: duplicate check → create Keycloak user → persist account → publish event.
 */
public class RegisterAccountUseCaseImpl implements RegisterAccountUseCase {

    private final AccountRepository accountRepository;
    private final IdentityProviderPort identityProvider;
    private final DomainEventPublisher eventPublisher;

    public RegisterAccountUseCaseImpl(
            AccountRepository accountRepository,
            IdentityProviderPort identityProvider,
            DomainEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.identityProvider = identityProvider;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public RegisterAccountResult register(RegisterAccountCommand command) {
        Email email = new Email(command.email());

        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(command.email());
        }

        Account account = Account.register(command.email(), command.firstName(), command.lastName());

        UUID keycloakUserId = identityProvider.createUser(
                command.email(),
                command.password(),
                command.firstName(),
                command.lastName(),
                account.getTenantId());

        identityProvider.assignRole(keycloakUserId, "ADMIN");
        account.setKeycloakUserId(keycloakUserId);

        Account saved = accountRepository.save(account);
        eventPublisher.publish(saved.pullDomainEvents());

        return new RegisterAccountResult(saved.getId(), keycloakUserId, email.value());
    }
}
