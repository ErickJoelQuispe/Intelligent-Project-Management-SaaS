package com.epm.ai.infrastructure.adapter.in.rest.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for AI chat.
 */
public record ChatRequest(

        @NotBlank(message = "projectId must not be blank")
        String projectId,

        @NotBlank(message = "message must not be blank")
        @Size(max = 2000, message = "message must not exceed 2000 characters")
        String message,

        /** Previous turns in the conversation — up to 20 entries. */
        @Size(max = 20, message = "history must not exceed 20 entries")
        List<ChatTurn> history,

        /** Task titles and statuses already in the project — for context. */
        @Size(max = 100)
        List<TaskSummaryDto> existingTasks
) {}
