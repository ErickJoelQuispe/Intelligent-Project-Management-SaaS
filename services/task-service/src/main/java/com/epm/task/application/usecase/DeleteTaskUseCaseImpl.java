package com.epm.task.application.usecase;

import java.util.UUID;

import com.epm.task.domain.exception.ProjectMembershipRequiredException;
import com.epm.task.domain.exception.TaskNotFoundException;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.DeleteTaskUseCase;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TaskRepository;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;

/**
 * Implementation of {@link DeleteTaskUseCase}.
 *
 * <p>Deletes the root task and all its subtasks atomically by delegating to
 * {@link TransactionalOutboxWriter#publishAndDeleteWithSubtasks(Task)}, which
 * performs the bulk subtask delete, outbox event publish, and root task delete
 * in a SINGLE transaction. A failure in any step rolls back all, preventing
 * split-brain states (e.g. subtasks deleted but root still exists with no event).
 *
 * <p>Membership check (Feign HTTP call, cached up to 30 s) is performed BEFORE the
 * write transaction so the DB connection is not held during the network round-trip.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class DeleteTaskUseCaseImpl implements DeleteTaskUseCase {

    private final TaskRepository taskRepository;
    private final TransactionalOutboxWriter outboxWriter;
    private final ProjectMembershipPort membershipPort;

    public DeleteTaskUseCaseImpl(TaskRepository taskRepository,
            TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort) {
        this.taskRepository = taskRepository;
        this.outboxWriter = outboxWriter;
        this.membershipPort = membershipPort;
    }

    /**
     * Deletes the task and all its subtasks atomically.
     *
     * <p>Subtask bulk-delete, outbox event publish, and root task delete are all
     * delegated to {@link TransactionalOutboxWriter#publishAndDeleteWithSubtasks(Task)}
     * and happen within a single transaction. Atomicity is the responsibility of the
     * infrastructure boundary (the writer), not this use case.
     *
     * <p>A single {@code TaskDeleted} domain event is emitted for the root; subtask
     * deletion is implied and not individually evented.
     *
     * @param taskId   root task id
     * @param tenantId tenant scope
     * @param callerId caller subject (used for membership check)
     */
    @Override
    public void execute(UUID taskId, UUID tenantId, UUID callerId) {
        // Load root first to obtain projectId for the membership check.
        Task root = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(taskId, tenantId));

        // Membership check BEFORE the write transaction (Feign call outside @Transactional).
        boolean isMember = membershipPort.isMember(root.getProjectId(), callerId, tenantId);
        if (!isMember) {
            throw new ProjectMembershipRequiredException(callerId, root.getProjectId());
        }

        // Raise the TaskDeleted domain event on the aggregate.
        root.markDeleted();

        // Delegate the entire atomic delete (subtasks + events + root) to the writer.
        // This ensures bulkDeleteSubtasks, eventPublisher.publish, and deleteByIdAndTenantId
        // all participate in ONE transaction — no split-brain possible.
        outboxWriter.publishAndDeleteWithSubtasks(root);
    }
}
