package com.epm.auth.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.auth.domain.model.UserSession;
import com.epm.auth.domain.port.in.GetUserSessionsUseCase;
import com.epm.auth.domain.port.out.IdentityProviderPort;

/**
 * Implementation of {@link GetUserSessionsUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 * Delegates to the identity provider. On circuit-breaker fallback the adapter
 * returns an empty list, which this use case transparently passes through.
 */
public class GetUserSessionsUseCaseImpl implements GetUserSessionsUseCase {

    private final IdentityProviderPort identityProviderPort;

    public GetUserSessionsUseCaseImpl(IdentityProviderPort identityProviderPort) {
        this.identityProviderPort = identityProviderPort;
    }

    @Override
    public List<UserSession> execute(UUID keycloakUserId) {
        return identityProviderPort.getUserSessions(keycloakUserId);
    }
}
