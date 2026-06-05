package com.epm.ai.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for AI chat.
 */
public record ChatRequest(

        @NotBlank(message = "projectId must not be blank")
        String projectId,

        @NotBlank(message = "message must not be blank")
        String message
) {}
