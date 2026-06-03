package com.epm.task.application.usecase;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.GetTaskKanbanUseCase;
import com.epm.task.domain.port.in.result.KanbanResult;
import com.epm.task.domain.port.out.KanbanTaskRow;
import com.epm.task.domain.port.out.KanbanViewRepository;

/**
 * Implementation of {@link GetTaskKanbanUseCase}.
 *
 * <p>Groups tasks by status and ensures all 5 status keys are always present
 * in the result (empty list if no tasks for that status).
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class GetTaskKanbanUseCaseImpl implements GetTaskKanbanUseCase {

    private final KanbanViewRepository kanbanViewRepository;

    public GetTaskKanbanUseCaseImpl(KanbanViewRepository kanbanViewRepository) {
        this.kanbanViewRepository = kanbanViewRepository;
    }

    @Override
    public KanbanResult execute(UUID projectId, UUID tenantId) {
        List<KanbanTaskRow> rows = kanbanViewRepository.findKanbanByProjectIdAndTenantId(projectId, tenantId);

        Map<TaskStatus, List<KanbanTaskRow>> columns = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            columns.put(status, new ArrayList<>());
        }

        for (KanbanTaskRow row : rows) {
            columns.get(row.status()).add(row);
        }

        return new KanbanResult(columns);
    }
}
