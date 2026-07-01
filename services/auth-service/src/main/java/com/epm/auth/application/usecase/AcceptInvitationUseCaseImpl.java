package com.epm.auth.application.usecase;

import java.util.UUID;

import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.port.in.AcceptInvitationUseCase;
import com.epm.auth.domain.port.in.command.AcceptInvitationCommand;
import com.epm.auth.domain.port.out.AccountRegistrationTransaction;
import com.epm.auth.domain.port.out.AccountRepository;
import com.epm.auth.domain.port.out.IdentityProviderPort;
import com.epm.auth.domain.port.out.InvitationValidationPort;
import com.epm.auth.domain.port.out.InvitationValidationResult;

/**
 * Implementation of {@link AcceptInvitationUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <h2>Orchestration order</h2>
 * <ol>
 *   <li>Validate the invitation token via user-service (Feign). Propagates domain exceptions
 *       for 404/410/409 immediately — no Keycloak call occurs if the token is invalid.</li>
 *   <li>Create Keycloak user using the email, name, and password from the command, and the
 *       {@code tenantId} from the invitation result. CRITICAL: the tenantId is NEVER
 *       regenerated here — it comes from the invitation so the invited user joins the
 *       <em>inviting</em> tenant, not a new one.</li>
 *   <li>Assign the role from the invitation result (VIEWER). CRITICAL: NEVER hardcoded "ADMIN".</li>
 *   <li>Link the Keycloak userId to the new Account aggregate, which records
 *       {@code AccountRegisteredEvent}.</li>
 *   <li>Mark the invitation token used via user-service.</li>
 *   <li>Atomically persist the account and publish the outbox event via
 *       {@link AccountRegistrationTransaction}. The downstream user-service consumer
 *       will create the UserProfile in the inviting tenant on receiving this event.</li>
 * </ol>
 *
 * <h2>Compensation</h2>
 * <p>If any step after Keycloak user creation fails, a best-effort compensation
 * deletes the orphaned Keycloak user and re-throws the original exception.
 */
public class AcceptInvitationUseCaseImpl implements AcceptInvitationUseCase {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AcceptInvitationUseCaseImpl.class);

    private final InvitationValidationPort invitationValidationPort;
    private final IdentityProviderPort identityProvider;
    private final AccountRepository accountRepository;
    private final AccountRegistrationTransaction registrationTransaction;

    public AcceptInvitationUseCaseImpl(
            InvitationValidationPort invitationValidationPort,
            IdentityProviderPort identityProvider,
            AccountRepository accountRepository,
            AccountRegistrationTransaction registrationTransaction) {
        this.invitationValidationPort = invitationValidationPort;
        this.identityProvider = identityProvider;
        this.accountRepository = accountRepository;
        this.registrationTransaction = registrationTransaction;
    }

    @Override
    public void accept(AcceptInvitationCommand command) {
        // Step 1: Validate token — may throw InvitationToken{Invalid,Expired,AlreadyUsed}Exception
        InvitationValidationResult invitation =
                invitationValidationPort.validateToken(command.token());

        // Step 2: Create Keycloak user.
        // tenantId comes from the invitation — NEVER regenerated.
        UUID keycloakUserId = identityProvider.createUser(
                invitation.email(),
                command.password(),
                command.firstName(),
                command.lastName(),
                invitation.tenantId());

        // From here on, a Keycloak user exists. Any subsequent failure triggers compensation.
        try {
            // Step 3: Assign role from the invitation (VIEWER). NEVER hardcoded.
            identityProvider.assignRole(keycloakUserId, invitation.role());

            // Step 4: Build the Account aggregate with the inviting tenant's tenantId.
            Account account = Account.register(
                    invitation.tenantId(),
                    invitation.email(),
                    command.firstName(),
                    command.lastName());

            // Records AccountRegisteredEvent with real keycloakUserId.
            account.linkKeycloakUser(keycloakUserId);

            // Step 5: Mark invitation used in user-service.
            invitationValidationPort.markUsed(invitation.invitationId());

            // Step 6: Atomically persist account + outbox event row.
            // The AccountRegisteredEvent in the outbox triggers user-service to create a UserProfile
            // in the inviting tenant via the auth.account.registered Kafka topic.
            registrationTransaction.saveAndPublish(account);

        } catch (Exception ex) {
            // Best-effort compensation: remove the orphaned Keycloak user.
            try {
                identityProvider.deleteUser(keycloakUserId);
            } catch (Exception compensationEx) {
                log.warn("Failed to compensate orphaned Keycloak user {} during accept-invitation: {}",
                        keycloakUserId, compensationEx.getMessage(), compensationEx);
            }
            throw ex;
        }
    }
}
