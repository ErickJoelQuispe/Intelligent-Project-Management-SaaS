package com.epm.project.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new project.
 */
public record CreateProjectRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        String visibility) {
}
