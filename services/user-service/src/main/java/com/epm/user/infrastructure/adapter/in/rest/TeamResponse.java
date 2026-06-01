package com.epm.user.infrastructure.adapter.in.rest;

import java.util.List;
import java.util.UUID;

/**
 * Team response DTO.
 */
public record TeamResponse(
        UUID id,
        UUID tenantId,
        UUID ownerId,
        String name,
        String description,
        List<MemberResponse> members) {
}
