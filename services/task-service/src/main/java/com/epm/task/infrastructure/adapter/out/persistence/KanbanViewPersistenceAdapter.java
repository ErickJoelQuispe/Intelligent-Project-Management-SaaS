package com.epm.task.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.port.out.KanbanTaskRow;
import com.epm.task.domain.port.out.KanbanViewRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link KanbanViewRepository} using the {@code task_kanban_view}
 * materialized view.
 */
@Component
public class KanbanViewPersistenceAdapter implements KanbanViewRepository {

    private final KanbanViewJpaRepository kanbanViewJpaRepo;

    public KanbanViewPersistenceAdapter(KanbanViewJpaRepository kanbanViewJpaRepo) {
        this.kanbanViewJpaRepo = kanbanViewJpaRepo;
    }

    @Override
    public List<KanbanTaskRow> findKanbanByProjectIdAndTenantId(UUID projectId, UUID tenantId) {
        return kanbanViewJpaRepo.findByProjectIdAndTenantId(projectId, tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    private KanbanTaskRow toDomain(KanbanViewJpaEntity entity) {
        return new KanbanTaskRow(
                entity.getTaskId(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getAssigneeId(),
                entity.getDeadline(),
                entity.getParentTaskId());
    }
}
