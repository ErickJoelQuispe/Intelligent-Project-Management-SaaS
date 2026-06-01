package com.epm.user.domain.port.in.result;

import java.time.Instant;
import java.util.UUID;

import com.epm.user.domain.model.TeamRole;

/**
 * Team membership result.
 */
public record MemberResult(UUID userId, TeamRole role, Instant joinedAt) {
}
