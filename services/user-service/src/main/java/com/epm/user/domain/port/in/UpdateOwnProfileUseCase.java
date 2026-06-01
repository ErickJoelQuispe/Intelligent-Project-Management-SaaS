package com.epm.user.domain.port.in;

import java.util.UUID;

import com.epm.user.domain.port.in.command.UpdateProfileCommand;
import com.epm.user.domain.port.in.result.UserProfileResult;

/**
 * Driving port: updates the calling user's profile.
 */
public interface UpdateOwnProfileUseCase {

    /**
     * Updates the profile for the given user.
     *
     * @param userId   the authenticated user's ID
     * @param tenantId the authenticated user's tenant
     * @param command  the update command
     * @return the updated profile result
     */
    UserProfileResult updateProfile(UUID userId, UUID tenantId, UpdateProfileCommand command);
}
