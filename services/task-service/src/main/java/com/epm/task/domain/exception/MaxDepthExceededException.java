package com.epm.task.domain.exception;

import java.util.UUID;

/**
 * Thrown when creating a subtask under another subtask would exceed the maximum allowed depth.
 *
 * <p>Maximum allowed hierarchy depth is 2 levels (root task → subtask).
 * A grandchild (subtask of a subtask) is not permitted.
 */
public class MaxDepthExceededException extends RuntimeException {

    public MaxDepthExceededException(UUID parentTaskId) {
        super(String.format(
                "Cannot create subtask under task %s: maximum task depth (2 levels) exceeded",
                parentTaskId));
    }
}
