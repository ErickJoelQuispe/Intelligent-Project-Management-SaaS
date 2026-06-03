package com.epm.task.infrastructure.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for changing a task's status.
 */
public record ChangeStatusRequest(
        @NotBlank(message = "status is required")
        String status) {
}
