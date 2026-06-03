package com.epm.task.domain.port.in;

import com.epm.task.domain.port.in.command.ChangeStatusCommand;
import com.epm.task.domain.port.in.result.TaskResult;

/**
 * Driving port: changes the status of a task.
 */
public interface ChangeTaskStatusUseCase {

    TaskResult execute(ChangeStatusCommand command);
}
