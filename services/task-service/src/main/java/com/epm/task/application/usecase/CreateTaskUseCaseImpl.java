package com.epm.task.application.usecase;

import com.epm.task.domain.exception.ProjectMembershipRequiredException;
import com.epm.task.domain.model.ActivityLog;
import com.epm.task.domain.model.Task;
import com.epm.task.domain.port.in.CreateTaskUseCase;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.domain.port.in.result.TaskResult;
import com.epm.task.domain.port.out.ProjectMembershipPort;
import com.epm.task.domain.port.out.TransactionalOutboxWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Implementation of {@link CreateTaskUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>Membership check (Feign HTTP call) is performed BEFORE the write transaction
 * so the DB connection is not held during the network round-trip.
 * {@link TransactionalOutboxWriter#saveAndPublish} is the sole transactional boundary.
 */
public class CreateTaskUseCaseImpl implements CreateTaskUseCase {

    private final TransactionalOutboxWriter outboxWriter;
    private final ProjectMembershipPort membershipPort;
    private final MeterRegistry meterRegistry;

    public CreateTaskUseCaseImpl(TransactionalOutboxWriter outboxWriter,
            ProjectMembershipPort membershipPort,
            MeterRegistry meterRegistry) {
        this.outboxWriter = outboxWriter;
        this.membershipPort = membershipPort;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public TaskResult execute(CreateTaskCommand command) {
        // Membership check via Feign happens OUTSIDE the write transaction
        boolean isMember = membershipPort.isMember(command.projectId(), command.callerId(), command.tenantId());
        if (!isMember) {
            throw new ProjectMembershipRequiredException(command.callerId(), command.projectId());
        }

        Task task = Task.create(command);
        ActivityLog log = ActivityLog.create(task.getId(), command.tenantId(), "CREATED", command.callerId());

        Task saved = outboxWriter.saveAndPublish(task, log);

        Counter.builder("tasks.created")
                .tag("tenantId", command.tenantId().toString())
                .register(meterRegistry)
                .increment();

        return TaskMapper.toResult(saved);
    }
}
