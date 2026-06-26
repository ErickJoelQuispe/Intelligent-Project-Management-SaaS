package com.epm.auth.infrastructure.adapter.out.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.domain.model.UserSession;
import com.epm.auth.infrastructure.config.KeycloakProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;

/**
 * Unit tests for {@link KeycloakAdminAdapter}.
 *
 * <p>Uses a reflection-friendly subclass to inject a pre-built mock UserResource
 * and a pre-built mock RealmResource so we can verify calls without network or Spring context.
 */
class KeycloakAdminAdapterTest {

    private UserResource mockUserResource;
    private TestableKeycloakAdminAdapter adapter;

    @BeforeEach
    void setUp() {
        mockUserResource = mock(UserResource.class);
        KeycloakProperties props = new KeycloakProperties(
                "http://localhost:8180", "epm", "epm-backend", "test-secret");
        adapter = new TestableKeycloakAdminAdapter(props, mockUserResource);
    }

    // ── disableUser ────────────────────────────────────────────────────────────

    @Test
    void disableUser_callsUpdateWithEnabledFalse() {
        UUID userId = UUID.randomUUID();

        adapter.disableUser(userId);

        verify(mockUserResource).update(any(UserRepresentation.class));
    }

    @Test
    void disableUser_withAnyUserId_callsUpdate() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        adapter.disableUser(userId);

        verify(mockUserResource).update(any(UserRepresentation.class));
    }

    @Test
    void disableUser_whenUpdateThrows_propagatesException() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("Keycloak unavailable")).when(mockUserResource).update(any());

        assertThatThrownBy(() -> adapter.disableUser(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Keycloak unavailable");
    }

    // ── getUserSessions ────────────────────────────────────────────────────────

    @Test
    void getUserSessions_mapsRepresentationsToDomainRecords() {
        UUID userId = UUID.randomUUID();

        UserSessionRepresentation rep1 = new UserSessionRepresentation();
        rep1.setId("sid-1");
        rep1.setIpAddress("192.168.1.1");
        rep1.setStart(1700000000000L);
        rep1.setLastAccess(1700001000000L);

        UserSessionRepresentation rep2 = new UserSessionRepresentation();
        rep2.setId("sid-2");
        rep2.setIpAddress("10.0.0.2");
        rep2.setStart(1700002000000L);
        rep2.setLastAccess(1700003000000L);

        when(mockUserResource.getUserSessions()).thenReturn(List.of(rep1, rep2));

        List<UserSession> sessions = adapter.getUserSessions(userId);

        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).sessionId()).isEqualTo("sid-1");
        assertThat(sessions.get(0).ipAddress()).isEqualTo("192.168.1.1");
        assertThat(sessions.get(0).started()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
        assertThat(sessions.get(0).lastAccess()).isEqualTo(Instant.ofEpochMilli(1700001000000L));
        assertThat(sessions.get(1).sessionId()).isEqualTo("sid-2");
    }

    // ── TRIANGULATE: empty session list ───────────────────────────────────────

    @Test
    void getUserSessions_whenNoSessions_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(mockUserResource.getUserSessions()).thenReturn(List.of());

        List<UserSession> sessions = adapter.getUserSessions(userId);

        assertThat(sessions).isEmpty();
    }

    // ── revokeSession ──────────────────────────────────────────────────────────

    @Test
    void revokeSession_callsDeleteSessionOnRealm() {
        adapter.revokeSession("session-abc");

        verify(adapter.getMockRealmResource()).deleteSession("session-abc", false);
    }

    // ── TRIANGULATE: different session ID ─────────────────────────────────────

    @Test
    void revokeSession_withDifferentSessionId_callsDeleteSession() {
        adapter.revokeSession("session-xyz-999");

        verify(adapter.getMockRealmResource()).deleteSession("session-xyz-999", false);
    }

    // ── Inner testable subclass ────────────────────────────────────────────────

    /**
     * Subclass that overrides protected hooks to inject mocks, bypassing the real Keycloak HTTP call.
     */
    static class TestableKeycloakAdminAdapter extends KeycloakAdminAdapter {

        private final UserResource userResource;
        private final org.keycloak.admin.client.resource.RealmResource mockRealmResource;

        TestableKeycloakAdminAdapter(KeycloakProperties props, UserResource userResource) {
            super(props);
            this.userResource = userResource;
            this.mockRealmResource = mock(org.keycloak.admin.client.resource.RealmResource.class);
        }

        @Override
        protected org.keycloak.admin.client.resource.UserResource getUserResource(UUID keycloakUserId) {
            return userResource;
        }

        @Override
        protected org.keycloak.admin.client.resource.RealmResource getRealmResource() {
            return mockRealmResource;
        }

        org.keycloak.admin.client.resource.RealmResource getMockRealmResource() {
            return mockRealmResource;
        }
    }
}
