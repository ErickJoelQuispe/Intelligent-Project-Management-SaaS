package com.epm.task.domain.port.in;

import com.epm.task.domain.port.in.command.CreateSubtaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;

/**
 * Driving port: creates a subtask under an existing root task.
 */
public interface CreateSubtaskUseCase {

    TaskResult execute(CreateSubtaskCommand command);
}
