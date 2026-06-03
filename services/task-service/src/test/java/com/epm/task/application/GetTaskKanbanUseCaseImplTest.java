package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.epm.task.application.usecase.GetTaskKanbanUseCaseImpl;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.result.KanbanResult;
import com.epm.task.domain.port.out.KanbanTaskRow;
import com.epm.task.domain.port.out.KanbanViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for GetTaskKanbanUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class GetTaskKanbanUseCaseImplTest {

    @Mock KanbanViewRepository kanbanViewRepository;

    GetTaskKanbanUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTaskKanbanUseCaseImpl(kanbanViewRepository);
    }

    @Test
    void execute_returnsTasksGroupedByStatus() {
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        KanbanTaskRow row1 = new KanbanTaskRow(
                UUID.randomUUID(), "Task A", TaskStatus.TODO, TaskPriority.HIGH, null, null, null);
        KanbanTaskRow row2 = new KanbanTaskRow(
                UUID.randomUUID(), "Task B", TaskStatus.IN_PROGRESS, TaskPriority.LOW, null, null, null);

        when(kanbanViewRepository.findKanbanByProjectIdAndTenantId(projectId, tenantId))
                .thenReturn(List.of(row1, row2));

        KanbanResult result = useCase.execute(projectId, tenantId);

        // All 5 status keys must be present
        assertThat(result.columns()).containsKeys(
                TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW,
                TaskStatus.DONE, TaskStatus.CANCELLED);
        assertThat(result.columns().get(TaskStatus.TODO)).hasSize(1);
        assertThat(result.columns().get(TaskStatus.TODO).get(0).title()).isEqualTo("Task A");
        assertThat(result.columns().get(TaskStatus.IN_PROGRESS)).hasSize(1);
        assertThat(result.columns().get(TaskStatus.DONE)).isEmpty();
    }

    @Test
    void execute_emptyProject_returnsAllStatusKeysWithEmptyLists() {
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(kanbanViewRepository.findKanbanByProjectIdAndTenantId(projectId, tenantId))
                .thenReturn(List.of());

        KanbanResult result = useCase.execute(projectId, tenantId);

        assertThat(result.columns()).hasSize(5);
        assertThat(result.columns().get(TaskStatus.TODO)).isEmpty();
        assertThat(result.columns().get(TaskStatus.IN_PROGRESS)).isEmpty();
        assertThat(result.columns().get(TaskStatus.IN_REVIEW)).isEmpty();
        assertThat(result.columns().get(TaskStatus.DONE)).isEmpty();
        assertThat(result.columns().get(TaskStatus.CANCELLED)).isEmpty();
    }
}
