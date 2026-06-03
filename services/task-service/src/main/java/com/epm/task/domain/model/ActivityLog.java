package com.epm.task.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * Entity that records audit actions performed on a Task.
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public class ActivityLog {

    private final UUID id;
    private final UUID taskId;
    private final UUID tenantId;
    private final String action;
    private final UUID actorId;
    private final String detail;
    private final Instant createdAt;

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new ActivityLog entry without a detail message.
     *
     * @param taskId   the task this log belongs to
     * @param tenantId tenant scope
     * @param action   a short description of the action (e.g. "STATUS_CHANGED")
     * @param actorId  the profile performing the action
     * @return a new ActivityLog with generated id and current timestamp
     */
    public static ActivityLog create(UUID taskId, UUID tenantId, String action, UUID actorId) {
        return new ActivityLog(
                UuidCreator.getTimeOrderedEpoch(),
                taskId,
                tenantId,
                action,
                actorId,
                null,
                Instant.now());
    }

    /**
     * Creates a new ActivityLog entry with a detail message.
     *
     * @param taskId   the task this log belongs to
     * @param tenantId tenant scope
     * @param action   a short description of the action
     * @param actorId  the profile performing the action
     * @param detail   optional detail string (nullable)
     * @return a new ActivityLog with generated id and current timestamp
     */
    public static ActivityLog createWithDetail(UUID taskId, UUID tenantId,
            String action, UUID actorId, String detail) {
        return new ActivityLog(
                UuidCreator.getTimeOrderedEpoch(),
                taskId,
                tenantId,
                action,
                actorId,
                detail,
                Instant.now());
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    private ActivityLog(UUID id, UUID taskId, UUID tenantId,
            String action, UUID actorId, String detail, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.action = action;
        this.actorId = actorId;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getTaskId() { return taskId; }

    public UUID getTenantId() { return tenantId; }

    public String getAction() { return action; }

    public UUID getActorId() { return actorId; }

    public String getDetail() { return detail; }

    public Instant getCreatedAt() { return createdAt; }
}
