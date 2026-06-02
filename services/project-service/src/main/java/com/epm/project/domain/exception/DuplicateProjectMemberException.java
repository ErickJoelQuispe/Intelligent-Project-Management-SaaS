package com.epm.project.domain.exception;

import java.util.UUID;

/**
 * Thrown when a profile is already an active member of a project.
 */
public class DuplicateProjectMemberException extends RuntimeException {

    public DuplicateProjectMemberException(UUID profileId, UUID projectId) {
        super("Profile " + profileId + " is already a member of project " + projectId);
    }
}
