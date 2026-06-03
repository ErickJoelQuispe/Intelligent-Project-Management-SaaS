package com.epm.task.domain.port.in;

import com.epm.task.domain.port.in.command.AssignTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;

/**
 * Driving port: assigns a task to a user.
 */
public interface AssignTaskUseCase {

    TaskResult execute(AssignTaskCommand command);
}
