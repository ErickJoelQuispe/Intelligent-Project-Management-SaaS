package com.epm.user.domain.port.in.result;

import java.util.List;
import java.util.UUID;

/**
 * Result of a team query or creation.
 */
public record TeamResult(
        UUID id,
        UUID tenantId,
        UUID ownerId,
        String name,
        String description,
        List<MemberResult> members) {
}
