package com.epm.task.application.usecase;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.GetSubtasksUseCase;
import com.epm.task.domain.port.out.TaskRepository;

/**
 * Implementation of {@link GetSubtasksUseCase}.
 *
 * <p>Delegates directly to the repository — no business rules apply to reads.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class GetSubtasksUseCaseImpl implements GetSubtasksUseCase {

    private final TaskRepository taskRepository;

    public GetSubtasksUseCaseImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public List<Task> getSubtasks(UUID parentTaskId, UUID tenantId) {
        return taskRepository.findSubtasksByParentId(parentTaskId, tenantId);
    }
}
