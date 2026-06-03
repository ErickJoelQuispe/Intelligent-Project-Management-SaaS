package com.epm.task.infrastructure.adapter.in.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing task.
 */
public record UpdateTaskRequest(
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must not exceed 255 characters")
        String title,

        String description,

        String priority,

        LocalDate deadline) {
}
