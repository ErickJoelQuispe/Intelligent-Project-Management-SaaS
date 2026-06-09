package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.task.application.usecase.ListTasksByProjectUseCaseImpl;
import com.epm.task.domain.model.PageResult;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.model.TaskStatus;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for ListTasksByProjectUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class ListTasksByProjectUseCaseImplTest {

    @Mock
    TaskRepository taskRepository;

    ListTasksByProjectUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListTasksByProjectUseCaseImpl(taskRepository);
    }

    @Test
    void execute_returnsPagedTaskResults() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Task task = Task.reconstitute(
                UUID.randomUUID(), tenantId, projectId, null,
                "Task A", null, TaskStatus.TODO, TaskPriority.MEDIUM,
                null, null, Instant.now(), Instant.now());
        PageResult<Task> taskPage = new PageResult<>(List.of(task), 1L, 1, 10, 0);

        when(taskRepository.findAllByProjectIdAndTenantId(eq(projectId), eq(tenantId), eq(0), eq(10)))
                .thenReturn(taskPage);

        PageResult<TaskResult> result = useCase.execute(projectId, tenantId, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).title()).isEqualTo("Task A");
        assertThat(result.content().get(0).tenantId()).isEqualTo(tenantId);
    }

    @Test
    void execute_emptyProject_returnsEmptyPage() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        PageResult<Task> emptyPage = new PageResult<>(List.of(), 0L, 0, 10, 0);

        when(taskRepository.findAllByProjectIdAndTenantId(eq(projectId), eq(tenantId), eq(0), eq(10)))
                .thenReturn(emptyPage);

        PageResult<TaskResult> result = useCase.execute(projectId, tenantId, 0, 10);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }
}
