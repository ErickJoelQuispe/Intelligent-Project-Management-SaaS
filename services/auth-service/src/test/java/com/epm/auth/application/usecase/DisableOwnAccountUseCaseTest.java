package com.epm.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.domain.port.out.IdentityProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DisableOwnAccountUseCaseImpl}.
 *
 * <p>Strict TDD — RED written first; all paths from spec scenarios covered.
 */
class DisableOwnAccountUseCaseTest {

    private IdentityProviderPort identityProvider;
    private DisableOwnAccountUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        identityProvider = mock(IdentityProviderPort.class);
        useCase = new DisableOwnAccountUseCaseImpl(identityProvider);
    }

    // ── RED: adapter is called with the exact UUID ─────────────────────────────

    @Test
    void execute_callsIdentityProviderWithCorrectUserId() {
        UUID userId = UUID.randomUUID();

        useCase.execute(userId);

        verify(identityProvider).disableUser(userId);
    }

    // ── TRIANGULATE: different UUID also delegates to adapter ──────────────────

    @Test
    void execute_withDifferentUserId_callsIdentityProviderWithThatId() {
        UUID userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        useCase.execute(userId);

        verify(identityProvider).disableUser(userId);
    }

    // ── Exception propagation: IdentityProviderException rethrown as-is ───────

    @Test
    void execute_whenIdentityProviderThrows_propagatesException() {
        UUID userId = UUID.randomUUID();
        IdentityProviderException expected = new IdentityProviderException("Keycloak down", 30);
        doThrow(expected).when(identityProvider).disableUser(userId);

        assertThatThrownBy(() -> useCase.execute(userId))
                .isInstanceOf(IdentityProviderException.class)
                .hasMessageContaining("Keycloak down");
    }
}
