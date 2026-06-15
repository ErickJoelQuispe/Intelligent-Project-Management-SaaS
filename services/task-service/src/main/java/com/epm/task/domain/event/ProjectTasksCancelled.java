package com.epm.task.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate domain event emitted when all tasks in a project are bulk-cancelled
 * as a result of a {@code ProjectArchived} event.
 *
 * <p><strong>Why aggregate?</strong> Cancelling potentially thousands of tasks via
 * a single bulk UPDATE would otherwise require emitting one {@code TaskStatusChanged}
 * event per task, producing unbounded outbox growth. Instead, a single aggregate event
 * is emitted. Downstream consumers (e.g. Kanban) refresh on schedule and do not
 * require per-task events for this cascade.
 */
public record ProjectTasksCancelled(
        UUID eventId,
        UUID projectId,
        UUID tenantId,
        int cancelledCount,
        Instant occurredAt) {
}
