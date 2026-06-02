package com.epm.project.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an existing project.
 */
public record UpdateProjectRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        String visibility) {
}
