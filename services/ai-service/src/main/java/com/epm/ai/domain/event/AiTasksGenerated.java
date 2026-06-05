package com.epm.ai.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.epm.ai.domain.model.TaskDraft;

/**
 * Domain event published when the AI generates a list of task drafts for a project.
 */
public record AiTasksGenerated(
        UUID eventId,
        String projectId,
        List<TaskDraft> tasks,
        String tenantId,
        String generatedBy,
        Instant occurredAt
) {
    public AiTasksGenerated {
        if (eventId == null) eventId = UUID.randomUUID();
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
