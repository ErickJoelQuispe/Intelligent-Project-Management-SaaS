package com.epm.task.infrastructure.adapter.in.web;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a subtask.
 */
public record CreateSubtaskRequest(
        @NotNull(message = "projectId is required")
        UUID projectId,

        @NotNull(message = "parentTaskId is required")
        UUID parentTaskId,

        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must not exceed 255 characters")
        String title,

        String description,

        @NotNull(message = "priority is required")
        String priority,

        LocalDate deadline,

        UUID assigneeId) {
}
