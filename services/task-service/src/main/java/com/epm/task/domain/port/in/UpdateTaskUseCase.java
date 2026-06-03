package com.epm.task.domain.port.in;

import com.epm.task.domain.port.in.command.UpdateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;

/**
 * Driving port: updates an existing task.
 */
public interface UpdateTaskUseCase {

    TaskResult execute(UpdateTaskCommand command);
}
