package com.epm.auth.application.usecase;

import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.domain.port.in.RevokeSessionUseCase;
import com.epm.auth.domain.port.out.IdentityProviderPort;

/**
 * Implementation of {@link RevokeSessionUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 * {@link IdentityProviderException} is re-thrown unchanged so the REST adapter
 * can map it to HTTP 503 with {@code Retry-After}.
 */
public class RevokeSessionUseCaseImpl implements RevokeSessionUseCase {

    private final IdentityProviderPort identityProviderPort;

    public RevokeSessionUseCaseImpl(IdentityProviderPort identityProviderPort) {
        this.identityProviderPort = identityProviderPort;
    }

    @Override
    public void execute(String sessionId) {
        identityProviderPort.revokeSession(sessionId);
    }
}
