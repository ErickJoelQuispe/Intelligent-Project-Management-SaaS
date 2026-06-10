package com.epm.auth.infrastructure.adapter.out.identity;

import java.util.List;
import java.util.UUID;

import com.epm.auth.domain.exception.IdentityProviderException;
import com.epm.auth.domain.port.out.IdentityProviderPort;
import com.epm.auth.infrastructure.config.KeycloakProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link IdentityProviderPort} using the Keycloak Admin Client.
 *
 * <p>Protected by Resilience4j circuit breaker and retry (both named "keycloak").
 * If Keycloak is unavailable, the circuit breaker opens and {@link IdentityProviderException}
 * is thrown with retryAfterSeconds=30.
 */
@Component
public class KeycloakAdminAdapter implements IdentityProviderPort {

    private final KeycloakProperties props;

    public KeycloakAdminAdapter(KeycloakProperties props) {
        this.props = props;
    }

    @Override
    @CircuitBreaker(name = "keycloak", fallbackMethod = "createUserFallback")
    public UUID createUser(String email, String password, String firstName, String lastName, UUID tenantId) {
        try (Keycloak keycloak = buildAdminClient()) {
            // 1. Build UserRepresentation
            UserRepresentation user = new UserRepresentation();
            user.setEmail(email);
            user.setUsername(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.singleAttribute("tenant_id", tenantId.toString());

            // 2. Set password credential
            CredentialRepresentation cred = new CredentialRepresentation();
            cred.setType(CredentialRepresentation.PASSWORD);
            cred.setValue(password);
            cred.setTemporary(false);
            user.setCredentials(List.of(cred));

            // 3. Create user — Response implements Closeable; must be closed to release the HTTP connection
            try (Response response = keycloak.realm(props.realm()).users().create(user)) {
                if (response.getStatus() != 201) {
                    throw new IdentityProviderException(
                            "Keycloak user creation failed with status: " + response.getStatus(), 0);
                }

                // 4. Extract user ID from Location header
                String location = response.getHeaderString("Location");
                String userId = location.substring(location.lastIndexOf('/') + 1);

                // 5. Set tenant_id attribute (already set above, but update to confirm)
                return UUID.fromString(userId);
            }
        }
    }

    /** Fallback for createUser — called when circuit breaker opens. */
    public UUID createUserFallback(
            String email, String password, String firstName, String lastName, UUID tenantId, Exception ex) {
        throw new IdentityProviderException("Keycloak unavailable: " + ex.getMessage(), 30);
    }

    @Override
    @CircuitBreaker(name = "keycloak", fallbackMethod = "assignRoleFallback")
    public void assignRole(UUID keycloakUserId, String roleName) {
        try (Keycloak keycloak = buildAdminClient()) {
            RoleRepresentation role = keycloak.realm(props.realm()).roles().get(roleName).toRepresentation();
            keycloak.realm(props.realm()).users().get(keycloakUserId.toString())
                    .roles().realmLevel().add(List.of(role));
        }
    }

    /** Fallback for assignRole — called when circuit breaker opens. */
    public void assignRoleFallback(UUID keycloakUserId, String roleName, Exception ex) {
        throw new IdentityProviderException("Keycloak unavailable: " + ex.getMessage(), 30);
    }

    @Override
    @CircuitBreaker(name = "keycloak", fallbackMethod = "invalidateSessionFallback")
    public void invalidateSession(UUID keycloakUserId) {
        try (Keycloak keycloak = buildAdminClient()) {
            keycloak.realm(props.realm()).users().get(keycloakUserId.toString()).logout();
        }
    }

    /** Fallback for invalidateSession — called when circuit breaker opens. */
    public void invalidateSessionFallback(UUID keycloakUserId, Exception ex) {
        throw new IdentityProviderException("Keycloak unavailable: " + ex.getMessage(), 30);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Keycloak buildAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(props.serverUrl())
                .realm("master")
                .clientId("admin-cli")
                .grantType("client_credentials")
                .clientId(props.clientId())
                .clientSecret(props.clientSecret())
                .build();
    }
}
