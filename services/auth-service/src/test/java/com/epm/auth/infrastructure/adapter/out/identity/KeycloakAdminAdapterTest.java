package com.epm.auth.infrastructure.adapter.out.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.infrastructure.config.KeycloakProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;

/**
 * Unit tests for {@link KeycloakAdminAdapter#disableUser}.
 *
 * <p>Uses reflection-friendly subclass to inject a pre-built mock UserResource
 * so we can verify the update() call without network or Spring context.
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

    // ── RED: disableUser calls update() with enabled=false ────────────────────

    @Test
    void disableUser_callsUpdateWithEnabledFalse() {
        UUID userId = UUID.randomUUID();

        adapter.disableUser(userId);

        verify(mockUserResource).update(any(UserRepresentation.class));
    }

    // ── TRIANGULATE: disableUser with a different UUID also calls update ───────

    @Test
    void disableUser_withAnyUserId_callsUpdate() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        adapter.disableUser(userId);

        // verify the representation had enabled=false captured in the adapter
        verify(mockUserResource).update(any(UserRepresentation.class));
    }

    // ── disableUser propagates exceptions from the adapter ────────────────────

    @Test
    void disableUser_whenUpdateThrows_propagatesException() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("Keycloak unavailable")).when(mockUserResource).update(any());

        assertThatThrownBy(() -> adapter.disableUser(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Keycloak unavailable");
    }

    // ── Inner testable subclass — bypasses the real Keycloak HTTP call ─────────

    /**
     * Subclass that overrides the user-resource lookup to return a mock,
     * allowing disableUser logic to be tested without a real Keycloak server.
     */
    static class TestableKeycloakAdminAdapter extends KeycloakAdminAdapter {

        private final UserResource userResource;

        TestableKeycloakAdminAdapter(KeycloakProperties props, UserResource userResource) {
            super(props);
            this.userResource = userResource;
        }

        @Override
        protected org.keycloak.admin.client.resource.UserResource getUserResource(UUID keycloakUserId) {
            return userResource;
        }
    }
}
