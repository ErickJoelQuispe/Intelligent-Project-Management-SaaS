package com.epm.auth.application.usecase;

import java.util.UUID;

import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.domain.port.in.DisableOwnAccountUseCase;
import com.epm.auth.domain.port.out.IdentityProviderPort;

/**
 * Implementation of {@link DisableOwnAccountUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 * Delegates to the identity provider to disable the user account.
 * {@link IdentityProviderException} is re-thrown unchanged so the REST adapter
 * can map it to HTTP 503 with {@code Retry-After}.
 */
public class DisableOwnAccountUseCaseImpl implements DisableOwnAccountUseCase {

    private final IdentityProviderPort identityProviderPort;

    public DisableOwnAccountUseCaseImpl(IdentityProviderPort identityProviderPort) {
        this.identityProviderPort = identityProviderPort;
    }

    @Override
    public void execute(UUID keycloakUserId) {
        identityProviderPort.disableUser(keycloakUserId);
    }
}
