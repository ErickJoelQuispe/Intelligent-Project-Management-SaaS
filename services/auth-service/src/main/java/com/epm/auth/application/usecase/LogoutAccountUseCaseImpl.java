package com.epm.auth.application.usecase;

import java.util.Optional;
import java.util.UUID;

import com.epm.auth.domain.model.Account;
import com.epm.auth.domain.model.SecurityEvent;
import com.epm.auth.domain.port.in.LogoutAccountUseCase;
import com.epm.auth.domain.port.out.AccountRepository;
import com.epm.auth.domain.port.out.IdentityProviderPort;
import com.epm.auth.domain.port.out.SecurityEventRepository;

/**
 * Implementation of {@link LogoutAccountUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 * Revokes the Keycloak session, then records a LOGOUT security event.
 */
public class LogoutAccountUseCaseImpl implements LogoutAccountUseCase {

    private final IdentityProviderPort identityProvider;
    private final SecurityEventRepository securityEventRepository;
    private final AccountRepository accountRepository;

    public LogoutAccountUseCaseImpl(
            IdentityProviderPort identityProvider,
            SecurityEventRepository securityEventRepository,
            AccountRepository accountRepository) {
        this.identityProvider = identityProvider;
        this.securityEventRepository = securityEventRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void logout(UUID accountId, UUID tenantId, String ipAddress, String userAgent) {
        Optional<Account> accountOpt = accountRepository.findById(accountId);

        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            if (account.getKeycloakUserId() != null) {
                identityProvider.invalidateSession(account.getKeycloakUserId());
            }
        }

        SecurityEvent event = SecurityEvent.logout(tenantId, accountId, ipAddress, userAgent);
        securityEventRepository.save(event);
    }
}
