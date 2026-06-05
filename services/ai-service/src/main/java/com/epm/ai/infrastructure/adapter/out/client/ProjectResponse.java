package com.epm.ai.infrastructure.adapter.out.client;

import java.util.List;

/**
 * DTO for the project-service GET /api/v1/projects/{id} response.
 */
public record ProjectResponse(
        String id,
        String name,
        String description,
        String status,
        String updatedAt,
        List<String> memberNames
) {}
