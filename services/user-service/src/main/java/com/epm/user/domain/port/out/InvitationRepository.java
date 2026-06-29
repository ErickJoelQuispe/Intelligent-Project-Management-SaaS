package com.epm.user.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.Invitation;

/**
 * Driven port: persistence contract for {@link Invitation} aggregates.
 */
public interface InvitationRepository {

    /**
     * Persists a new or updated invitation.
     *
     * @param invitation the invitation to persist
     * @return the persisted invitation
     */
    Invitation save(Invitation invitation);

    /**
     * Finds an invitation by its token hash.
     *
     * @param tokenHash the SHA-256 hex hash of the plaintext token
     * @return the invitation if found
     */
    Optional<Invitation> findByTokenHash(String tokenHash);

    /**
     * Finds an invitation by its ID.
     *
     * @param id the invitation UUID
     * @return the invitation if found
     */
    Optional<Invitation> findById(UUID id);

    /**
     * Checks whether an active (not used, not expired) invitation already
     * exists for the given email within the tenant.
     *
     * @param email    the invitee's email address
     * @param tenantId the tenant
     * @return {@code true} if an active invitation exists
     */
    boolean existsActiveInvitation(String email, UUID tenantId);
}
