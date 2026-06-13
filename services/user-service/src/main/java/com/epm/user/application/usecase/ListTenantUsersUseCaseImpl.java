package com.epm.user.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.in.ListTenantUsersUseCase;
import com.epm.user.domain.port.out.UserProfileRepository;

/**
 * Implementation of {@link ListTenantUsersUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 * Results are DB-paginated; the {@code size} parameter is capped at
 * {@value #MAX_RESULTS} to prevent runaway queries.
 */
public class ListTenantUsersUseCaseImpl implements ListTenantUsersUseCase {

    static final int MAX_RESULTS = 100;

    private final UserProfileRepository profileRepository;

    public ListTenantUsersUseCaseImpl(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public List<UserProfile> listTenantUsers(UUID tenantId, int page, int size) {
        // Defensive clamping so PageRequest.of never throws on out-of-range input,
        // regardless of how the adapter validated it: page >= 0, 1 <= size <= MAX_RESULTS.
        int effectivePage = Math.max(0, page);
        int effectiveSize = Math.max(1, Math.min(size, MAX_RESULTS));
        return profileRepository.findPageByTenantId(tenantId, effectivePage, effectiveSize);
    }
}
