package com.epm.auth.domain.port.out;

import java.util.UUID;

/**
 * Driven port: identity provider (Keycloak) contract.
 *
 * <p>Implemented by the infrastructure layer (KeycloakAdminAdapter).
 */
public interface IdentityProviderPort {

    /**
     * Creates a user in the identity provider.
     *
     * @param email     user email
     * @param password  raw password
     * @param firstName first name
     * @param lastName  last name
     * @param tenantId  tenant UUID to set as user attribute
     * @return the newly created Keycloak user UUID
     * @throws com.epm.auth.domain.exception.IdentityProviderException if the call fails
     */
    UUID createUser(String email, String password, String firstName, String lastName, UUID tenantId);

    /**
     * Assigns a realm role to the Keycloak user.
     *
     * @param keycloakUserId the Keycloak user UUID
     * @param roleName       role name (e.g., "ADMIN")
     */
    void assignRole(UUID keycloakUserId, String roleName);

    /**
     * Invalidates all active sessions for the Keycloak user.
     *
     * @param keycloakUserId the Keycloak user UUID
     */
    void invalidateSession(UUID keycloakUserId);

    /**
     * Deletes a user from the identity provider.
     *
     * <p>This is a <em>best-effort</em> compensating action used during saga rollback
     * when account persistence fails after a Keycloak user was already created.
     * If deletion fails the error must be swallowed and logged; the original exception
     * must be re-thrown by the caller so the failure is not masked.
     *
     * @param keycloakUserId the Keycloak user UUID to delete
     */
    void deleteUser(UUID keycloakUserId);

    /**
     * Disables a user in the identity provider (soft-disable — account remains but cannot log in).
     *
     * <p>Used as the first step of the account deletion flow.
     * On failure, {@link com.epm.auth.domain.exception.IdentityProviderException} is thrown.
     *
     * @param keycloakUserId the Keycloak user UUID to disable
     */
    void disableUser(UUID keycloakUserId);
}
