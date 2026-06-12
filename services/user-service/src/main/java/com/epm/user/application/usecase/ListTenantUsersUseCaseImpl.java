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
 * Results are capped at 100; TODO: replace with cursor-based pagination.
 */
public class ListTenantUsersUseCaseImpl implements ListTenantUsersUseCase {

    private static final int MAX_RESULTS = 100;

    private final UserProfileRepository profileRepository;

    public ListTenantUsersUseCaseImpl(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public List<UserProfile> listTenantUsers(UUID tenantId) {
        List<UserProfile> all = profileRepository.findAllByTenantId(tenantId);
        // TODO: replace with cursor-based pagination once user counts grow beyond 100
        if (all.size() > MAX_RESULTS) {
            return all.subList(0, MAX_RESULTS);
        }
        return all;
    }
}
