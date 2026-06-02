package com.epm.project.infrastructure.adapter.in.rest;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for assigning a team to a project.
 */
public record AssignTeamRequest(
        @NotNull UUID teamId) {
}
