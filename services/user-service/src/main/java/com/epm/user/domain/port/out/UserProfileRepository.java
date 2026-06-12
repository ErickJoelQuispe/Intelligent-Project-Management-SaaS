package com.epm.user.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.UserProfile;

/**
 * Driven port: persistence for {@link UserProfile} aggregates.
 */
public interface UserProfileRepository {

    Optional<UserProfile> findByIdAndTenantId(UUID id, UUID tenantId);

    List<UserProfile> findAllByTenantId(UUID tenantId);

    UserProfile save(UserProfile profile);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
