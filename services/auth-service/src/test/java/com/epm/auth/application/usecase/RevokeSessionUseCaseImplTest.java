package com.epm.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.domain.port.out.IdentityProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RevokeSessionUseCaseImpl}.
 *
 * <p>Strict TDD — tests written first; all spec scenarios covered.
 */
class RevokeSessionUseCaseImplTest {

    private IdentityProviderPort identityProvider;
    private RevokeSessionUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        identityProvider = mock(IdentityProviderPort.class);
        useCase = new RevokeSessionUseCaseImpl(identityProvider);
    }

    // ── RED: delegates to identity provider with correct session ID ────────────

    @Test
    void execute_callsIdentityProviderWithCorrectSessionId() {
        String sessionId = "session-abc-123";

        useCase.execute(sessionId);

        verify(identityProvider).revokeSession(sessionId);
    }

    // ── TRIANGULATE: different session ID also delegates ──────────────────────

    @Test
    void execute_withDifferentSessionId_callsIdentityProviderWithThatId() {
        String sessionId = "ffffffff-ffff-ffff-ffff-ffffffffffff";

        useCase.execute(sessionId);

        verify(identityProvider).revokeSession(sessionId);
    }

    // ── Exception propagation ──────────────────────────────────────────────────

    @Test
    void execute_whenIdentityProviderThrows_propagatesException() {
        String sessionId = "session-fail";
        doThrow(new IdentityProviderException("Keycloak down", 30))
                .when(identityProvider).revokeSession(sessionId);

        assertThatThrownBy(() -> useCase.execute(sessionId))
                .isInstanceOf(IdentityProviderException.class)
                .hasMessageContaining("Keycloak down");
    }
}
