package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.epm.task.application.usecase.ListTasksByProjectUseCaseImpl;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Unit tests for ListTasksByProjectUseCaseImpl.
 */
@ExtendWith(MockitoExtension.class)
class ListTasksByProjectUseCaseImplTest {

    @Mock TaskRepository taskRepository;

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
        Page<Task> taskPage = new PageImpl<>(List.of(task));

        when(taskRepository.findAllByProjectIdAndTenantId(eq(projectId), eq(tenantId), any(Pageable.class)))
                .thenReturn(taskPage);

        Page<TaskResult> result = useCase.execute(projectId, tenantId, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Task A");
        assertThat(result.getContent().get(0).tenantId()).isEqualTo(tenantId);
    }

    @Test
    void execute_emptyProject_returnsEmptyPage() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(taskRepository.findAllByProjectIdAndTenantId(eq(projectId), eq(tenantId), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<TaskResult> result = useCase.execute(projectId, tenantId, 0, 10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
