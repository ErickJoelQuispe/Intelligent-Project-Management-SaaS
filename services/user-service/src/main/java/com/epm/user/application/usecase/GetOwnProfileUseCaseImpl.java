package com.epm.user.application.usecase;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.in.GetOwnProfileUseCase;
import com.epm.user.domain.port.in.dto.JwtClaimsDto;
import com.epm.user.domain.port.in.result.UserProfileResult;
import com.epm.user.domain.port.out.UserProfileRepository;

/**
 * Implementation of {@link GetOwnProfileUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 * Design D3: returns provisional profile from JWT claims when DB has no record.
 */
public class GetOwnProfileUseCaseImpl implements GetOwnProfileUseCase {

    private final UserProfileRepository profileRepository;

    public GetOwnProfileUseCaseImpl(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public UserProfileResult getProfile(UUID userId, UUID tenantId, JwtClaimsDto jwtClaims) {
        Optional<UserProfile> found = profileRepository.findByIdAndTenantId(userId, tenantId);
        if (found.isPresent()) {
            return toResult(found.get(), false);
        }
        // Provisional profile from JWT claims (D3)
        return new UserProfileResult(userId, tenantId, jwtClaims.email(),
                jwtClaims.firstName(), jwtClaims.lastName(), null, null, 0L, true);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UserProfileResult toResult(UserProfile profile, boolean provisional) {
        return new UserProfileResult(
                profile.getId(), profile.getTenantId(), profile.getEmail(),
                profile.getFirstName(), profile.getLastName(), profile.getBio(),
                profile.getAvatarUrl(), profile.getVersion(), provisional);
    }
}
