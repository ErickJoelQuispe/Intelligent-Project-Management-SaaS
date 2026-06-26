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
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link IdentityProviderPort} using the Keycloak Admin Client.
 *
 * <p>Protected by Resilience4j circuit breaker (named "keycloak").
 * If Keycloak is unavailable, the circuit breaker opens and {@link IdentityProviderException}
 * is thrown with retryAfterSeconds=30.
 */
@Component
public class KeycloakAdminAdapter implements IdentityProviderPort {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminAdapter.class);

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

    /**
     * {@inheritDoc}
     *
     * <p>Best-effort compensation: removes a Keycloak user created during a registration
     * that subsequently failed. The circuit breaker fallback logs the failure and does NOT
     * rethrow, so the original exception is never masked.
     */
    @Override
    @CircuitBreaker(name = "keycloak", fallbackMethod = "deleteUserFallback")
    public void deleteUser(UUID keycloakUserId) {
        try (Keycloak keycloak = buildAdminClient()) {
            keycloak.realm(props.realm()).users().get(keycloakUserId.toString()).remove();
        }
    }

    /**
     * Fallback for deleteUser — logs and swallows the error.
     * The original registration failure will still be re-thrown by the use case.
     */
    public void deleteUserFallback(UUID keycloakUserId, Exception ex) {
        log.warn("Best-effort Keycloak user deletion failed for user {}. "
                + "Manual cleanup may be required. Cause: {}", keycloakUserId, ex.getMessage());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets {@code enabled=false} on the Keycloak user representation.
     * This is the first step of the account deletion flow — it prevents the user from
     * logging in while the user-service soft-delete is being processed.
     */
    @Override
    @CircuitBreaker(name = "keycloak", fallbackMethod = "disableUserFallback")
    public void disableUser(UUID keycloakUserId) {
        UserResource userResource = getUserResource(keycloakUserId);
        UserRepresentation representation = new UserRepresentation();
        representation.setEnabled(false);
        userResource.update(representation);
    }

    /** Fallback for disableUser — called when circuit breaker opens. */
    public void disableUserFallback(UUID keycloakUserId, Exception ex) {
        throw new IdentityProviderException("Keycloak unavailable: " + ex.getMessage(), 30);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a {@link UserResource} for the given Keycloak user ID.
     *
     * <p>Protected to allow unit tests to override without a real Keycloak connection.
     */
    protected UserResource getUserResource(UUID keycloakUserId) {
        try (Keycloak keycloak = buildAdminClient()) {
            return keycloak.realm(props.realm()).users().get(keycloakUserId.toString());
        }
    }

    private Keycloak buildAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(props.serverUrl())
                .realm(props.realm())
                .grantType("client_credentials")
                .clientId(props.clientId())
                .clientSecret(props.clientSecret())
                .build();
    }
}
