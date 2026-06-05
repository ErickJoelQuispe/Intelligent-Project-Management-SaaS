package com.epm.ai.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for task generation.
 */
public record GenerateTasksRequest(

        @NotBlank(message = "projectId must not be blank")
        String projectId,

        @NotBlank(message = "description must not be blank")
        String description,

        boolean bypassCache
) {}
