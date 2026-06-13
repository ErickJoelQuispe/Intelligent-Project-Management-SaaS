package com.epm.user.application.usecase;

import java.util.UUID;

import com.epm.user.domain.exception.ProfileNotFoundException;
import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.in.UpdateOwnProfileUseCase;
import com.epm.user.domain.port.in.command.UpdateProfileCommand;
import com.epm.user.domain.port.in.result.UserProfileResult;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import com.epm.user.domain.port.out.UserProfileRepository;

/**
 * Implementation of {@link UpdateOwnProfileUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class UpdateOwnProfileUseCaseImpl implements UpdateOwnProfileUseCase {

    private final UserProfileRepository profileRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public UpdateOwnProfileUseCaseImpl(UserProfileRepository profileRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.profileRepository = profileRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public UserProfileResult updateProfile(UUID userId, UUID tenantId, UpdateProfileCommand command) {
        UserProfile profile = profileRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ProfileNotFoundException(userId));

        profile.update(command.firstName(), command.lastName(), command.bio(),
                command.avatarUrl(), command.version());

        UserProfile saved = outboxWriter.saveProfileAndPublish(profile);

        return new UserProfileResult(saved.getId(), saved.getTenantId(), saved.getEmail(),
                saved.getFirstName(), saved.getLastName(), saved.getBio(),
                saved.getAvatarUrl(), saved.getVersion(), false);
    }
}
