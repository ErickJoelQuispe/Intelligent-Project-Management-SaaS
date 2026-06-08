package com.epm.user.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.Team;

/**
 * Driven port: persistence for {@link Team} aggregates.
 */
public interface TeamRepository {

    Optional<Team> findByIdAndTenantId(UUID teamId, UUID tenantId);

    Team save(Team team);

    List<Team> findAllByMemberUserId(UUID userId, UUID tenantId);
}
