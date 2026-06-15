package com.epm.task.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.task.application.usecase.DeleteTaskUseCaseImpl;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for DeleteTaskUseCaseImpl.
 *
 * <p>Key invariant verified: the use case must delegate ALL of
 * bulk-delete-subtasks + outbox-publish + root-delete to
 * {@link TransactionalOutboxWriter#publishAndDeleteWithSubtasks(Task)} in ONE call,
 * ensuring atomicity is owned by the writer (infrastructure boundary), not the use case.
 */
@ExtendWith(MockitoExtension.class)
class DeleteTaskUseCaseImplTest {

    @Mock TaskRepository taskRepository;
    @Mock TransactionalOutboxWriter outboxWriter;
    @Mock ProjectMembershipPort membershipPort;

    DeleteTaskUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteTaskUseCaseImpl(taskRepository, outboxWriter, membershipPort);
    }

    /**
     * Happy path: the use case must call {@code publishAndDeleteWithSubtasks} (atomic writer)
     * and must NOT call {@code bulkDeleteSubtasks} directly (split-brain prevention).
     */
    @Test
    void execute_rootTask_delegatesAtomicDeleteToWriter() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Task rootTask = Task.create(new CreateTaskCommand(
                tenantId, projectId, callerId, "Root", null, TaskPriority.HIGH, null, null));
        rootTask.pullDomainEvents();

        when(taskRepository.findByIdAndTenantId(rootTask.getId(), tenantId))
                .thenReturn(Optional.of(rootTask));
        when(membershipPort.isMember(any(), any(), any())).thenReturn(true);

        useCase.execute(rootTask.getId(), tenantId, callerId);

        // MUST delegate to the atomic writer — subtask delete + event publish + root delete
        // all happen in ONE transaction inside the writer.
        verify(outboxWriter).publishAndDeleteWithSubtasks(rootTask);

        // MUST NOT call bulkDeleteSubtasks directly — that would be a separate committed
        // transaction and break atomicity (split-brain: subtasks gone but root still exists
        // if publishAndDeleteWithSubtasks subsequently fails).
        verify(taskRepository, never()).bulkDeleteSubtasks(any(), any());

        // saveAndPublish is for create/update flows — never called on delete.
        verify(outboxWriter, never()).saveAndPublish(any(), any());
    }

    @Test
    void execute_notFound_throwsTaskNotFoundException() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(taskId, tenantId, UUID.randomUUID()))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
