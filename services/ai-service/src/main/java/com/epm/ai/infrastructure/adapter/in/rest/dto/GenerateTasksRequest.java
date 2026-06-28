package com.epm.ai.infrastructure.adapter.in.rest.dto;

import java.util.List;

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

        boolean bypassCache,

        /** Titles of tasks that already exist in the project — used to avoid duplicates. */
        @Size(max = 100, message = "existingTaskTitles must not exceed 100 entries")
        List<String> existingTaskTitles
) {}
