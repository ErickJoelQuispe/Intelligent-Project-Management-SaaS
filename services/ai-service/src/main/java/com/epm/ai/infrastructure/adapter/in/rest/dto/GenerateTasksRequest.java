package com.epm.ai.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for task generation.
 */
public record GenerateTasksRequest(

        @NotBlank(message = "projectId must not be blank")
        String projectId,

        @NotBlank(message = "description must not be blank")
        @Size(max = 4000, message = "description must not exceed 4000 characters")
        String description,

        boolean bypassCache
) {}
