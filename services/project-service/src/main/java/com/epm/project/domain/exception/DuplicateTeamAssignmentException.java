package com.epm.project.domain.exception;

import java.util.UUID;

/**
 * Thrown when a team is already actively assigned to a project.
 */
public class DuplicateTeamAssignmentException extends RuntimeException {

    public DuplicateTeamAssignmentException(UUID teamId, UUID projectId) {
        super("Team " + teamId + " is already assigned to project " + projectId);
    }
}
