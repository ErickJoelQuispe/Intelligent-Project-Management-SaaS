package com.epm.user.application.usecase;

import java.util.UUID;

import com.epm.user.domain.exception.ProfileNotFoundException;
import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.in.DeleteOwnProfileUseCase;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import com.epm.user.domain.port.out.UserProfileRepository;

/**
 * Implementation of {@link DeleteOwnProfileUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Soft-deletes the authenticated user's profile by setting {@code deletedAt}
 * on the aggregate, then persisting via the outbox writer so the {@code ProfileDeleted}
 * domain event is published atomically.
 */
public class DeleteOwnProfileUseCaseImpl implements DeleteOwnProfileUseCase {

    private final UserProfileRepository profileRepository;
    private final TransactionalOutboxWriter outboxWriter;

    public DeleteOwnProfileUseCaseImpl(UserProfileRepository profileRepository,
            TransactionalOutboxWriter outboxWriter) {
        this.profileRepository = profileRepository;
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void execute(UUID userId) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ProfileNotFoundException(userId));

        profile.softDelete();

        outboxWriter.saveProfileAndPublish(profile);
    }
}
