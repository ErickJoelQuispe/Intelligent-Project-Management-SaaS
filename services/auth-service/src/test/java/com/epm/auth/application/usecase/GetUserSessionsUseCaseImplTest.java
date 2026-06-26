package com.epm.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.domain.model.UserSession;
import com.epm.auth.domain.port.out.IdentityProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GetUserSessionsUseCaseImpl}.
 *
 * <p>Strict TDD — tests written first; all spec scenarios covered.
 */
class GetUserSessionsUseCaseImplTest {

    private IdentityProviderPort identityProvider;
    private GetUserSessionsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        identityProvider = mock(IdentityProviderPort.class);
        useCase = new GetUserSessionsUseCaseImpl(identityProvider);
    }

    // ── RED: returns list from identity provider ───────────────────────────────

    @Test
    void execute_returnsMappedSessionsFromIdentityProvider() {
        UUID userId = UUID.randomUUID();
        List<UserSession> sessions = List.of(
                new UserSession("sid-1", "192.168.1.1", Instant.ofEpochMilli(1700000000000L), Instant.ofEpochMilli(1700001000000L)),
                new UserSession("sid-2", "10.0.0.2",   Instant.ofEpochMilli(1700002000000L), Instant.ofEpochMilli(1700003000000L))
        );
        when(identityProvider.getUserSessions(userId)).thenReturn(sessions);

        List<UserSession> result = useCase.execute(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).sessionId()).isEqualTo("sid-1");
        assertThat(result.get(0).ipAddress()).isEqualTo("192.168.1.1");
        verify(identityProvider).getUserSessions(userId);
    }

    // ── TRIANGULATE: empty list is handled correctly ───────────────────────────

    @Test
    void execute_whenNoActiveSessions_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(identityProvider.getUserSessions(userId)).thenReturn(List.of());

        List<UserSession> result = useCase.execute(userId);

        assertThat(result).isEmpty();
        verify(identityProvider).getUserSessions(userId);
    }

    // ── Exception propagation ──────────────────────────────────────────────────

    @Test
    void execute_whenIdentityProviderThrows_propagatesException() {
        UUID userId = UUID.randomUUID();
        doThrow(new IdentityProviderException("Keycloak down", 30))
                .when(identityProvider).getUserSessions(userId);

        assertThatThrownBy(() -> useCase.execute(userId))
                .isInstanceOf(IdentityProviderException.class)
                .hasMessageContaining("Keycloak down");
    }
}
