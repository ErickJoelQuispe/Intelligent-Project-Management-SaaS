package com.epm.auth.application.usecase;

import java.util.UUID;

import com.epm.auth.domain.exception.DuplicateEmailException;
import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.model.Email;
import com.epm.auth.domain.port.in.RegisterAccountUseCase;
import com.epm.auth.domain.port.in.command.RegisterAccountCommand;
import com.epm.auth.domain.port.in.result.RegisterAccountResult;
import com.epm.auth.domain.port.out.AccountRegistrationTransaction;
import com.epm.auth.domain.port.out.AccountRepository;
import com.epm.auth.domain.port.out.IdentityProviderPort;

/**
 * Implementation of {@link RegisterAccountUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <h2>Self-service signup model</h2>
 * <p>Each registration creates its own tenant. The {@code tenantId} is generated here
 * (using {@link UUID#randomUUID()} — plain JDK, zero framework coupling) and passed
 * explicitly into the domain factory. This keeps the tenancy decision visible and
 * auditable at the application boundary.
 *
 * <h2>Orchestration order</h2>
 * <ol>
 *   <li>Duplicate email check.</li>
 *   <li>Build domain aggregate (no event recorded yet; tenantId explicit).</li>
 *   <li>Create Keycloak user.</li>
 *   <li>Assign role — if this or any later step fails, compensate by deleting the
 *       Keycloak user (best-effort) and re-throw the original exception.</li>
 *   <li>Link Keycloak user on the aggregate (records {@code AccountRegisteredEvent}
 *       with the real keycloakUserId).</li>
 *   <li>Atomic persist-and-publish via {@link AccountRegistrationTransaction}.</li>
 * </ol>
 */
public class RegisterAccountUseCaseImpl implements RegisterAccountUseCase {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RegisterAccountUseCaseImpl.class);

    private final AccountRepository accountRepository;
    private final IdentityProviderPort identityProvider;
    private final AccountRegistrationTransaction registrationTransaction;

    public RegisterAccountUseCaseImpl(
            AccountRepository accountRepository,
            IdentityProviderPort identityProvider,
            AccountRegistrationTransaction registrationTransaction) {
        this.accountRepository = accountRepository;
        this.identityProvider = identityProvider;
        this.registrationTransaction = registrationTransaction;
    }

    @Override
    public RegisterAccountResult register(RegisterAccountCommand command) {
        Email email = new Email(command.email());

        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(command.email());
        }

        // Self-service signup: each registration gets its own tenant.
        // tenantId is generated here — not inside the aggregate — so the decision is explicit.
        UUID tenantId = UUID.randomUUID();
        Account account = Account.register(tenantId, command.email(), command.firstName(), command.lastName());

        UUID keycloakUserId = identityProvider.createUser(
                command.email(),
                command.password(),
                command.firstName(),
                command.lastName(),
                account.getTenantId());

        // From here on, a Keycloak user exists. Any exception must trigger best-effort compensation.
        try {
            identityProvider.assignRole(keycloakUserId, "ADMIN");

            // Records AccountRegisteredEvent with the real keycloakUserId.
            account.linkKeycloakUser(keycloakUserId);

            // Atomically persists the account row and the outbox event row in one transaction.
            Account saved = registrationTransaction.saveAndPublish(account);

            return new RegisterAccountResult(saved.getId(), keycloakUserId, email.value());
        } catch (Exception ex) {
            // Best-effort compensation: remove the orphaned Keycloak user.
            // If compensation itself fails, the error is swallowed so we re-throw the original.
            try {
                identityProvider.deleteUser(keycloakUserId);
            } catch (Exception compensationEx) {
                // Log only — never mask the original failure.
                log.warn("Failed to compensate orphaned Keycloak user {}: {}",
                        keycloakUserId, compensationEx.getMessage(), compensationEx);
            }
            throw ex;
        }
    }
}
