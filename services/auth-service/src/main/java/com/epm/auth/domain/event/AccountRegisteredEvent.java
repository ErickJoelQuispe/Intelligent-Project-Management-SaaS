package com.epm.auth.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Domain event emitted when a new Account is registered.
 *
 * <p>Immutable record — pure Java, no Spring annotations.
 * Published via the outbox pattern after aggregate persistence.
 */
public record AccountRegisteredEvent(
        UUID eventId,
        UUID accountId,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        UUID keycloakUserId,
        Instant occurredAt) {

    /**
     * Creates an event for a newly registered account.
     *
     * @param accountId       the new account's UUID
     * @param tenantId        the account's tenant UUID
     * @param email           normalized email
     * @param firstName       first name
     * @param lastName        last name
     * @param keycloakUserId  Keycloak user ID (may be null until set)
     */
    public static AccountRegisteredEvent of(
            UUID accountId,
            UUID tenantId,
            String email,
            String firstName,
            String lastName,
            UUID keycloakUserId) {
        return new AccountRegisteredEvent(
                UuidCreator.getTimeOrderedEpoch(),
                accountId,
                tenantId,
                email,
                firstName,
                lastName,
                keycloakUserId,
                Instant.now());
    }
}
