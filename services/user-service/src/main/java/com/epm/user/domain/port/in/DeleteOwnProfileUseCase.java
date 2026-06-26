package com.epm.user.domain.port.in;

import java.util.UUID;

/**
 * Port for soft-deleting the authenticated user's own profile.
 */
public interface DeleteOwnProfileUseCase {

    void execute(UUID userId);
}
