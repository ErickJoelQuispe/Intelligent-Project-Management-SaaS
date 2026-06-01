package com.epm.auth.domain.port.in.result;

import java.util.UUID;

/**
 * Result of a successful account registration.
 *
 * <p>Plain Java record — no framework annotations.
 */
public record RegisterAccountResult(
        UUID accountId,
        UUID keycloakUserId,
        String email) {}
