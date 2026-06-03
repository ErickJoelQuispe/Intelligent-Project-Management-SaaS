package com.epm.task.domain.port.in;

import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;

/**
 * Driving port: creates a new root task.
 */
public interface CreateTaskUseCase {

    TaskResult execute(CreateTaskCommand command);
}
