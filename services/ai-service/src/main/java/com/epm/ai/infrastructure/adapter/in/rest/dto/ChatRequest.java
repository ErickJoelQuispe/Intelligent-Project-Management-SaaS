package com.epm.ai.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for AI chat.
 */
public record ChatRequest(

        @NotBlank(message = "projectId must not be blank")
        String projectId,

        @NotBlank(message = "message must not be blank")
        @Size(max = 4000, message = "message must not exceed 4000 characters")
        String message
) {}
