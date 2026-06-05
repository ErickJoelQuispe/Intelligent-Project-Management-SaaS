package com.epm.ai.domain.event;

import com.epm.ai.domain.model.TaskDraft;
import com.epm.ai.domain.model.TaskPriority;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AiTasksGenerated domain event.
 */
class AiTasksGeneratedTest {

    @Test
    void construction_withAllFields_storesValues() {
        UUID eventId = UUID.randomUUID();
        List<TaskDraft> tasks = List.of(new TaskDraft("Deploy service", "Deploy to k8s", TaskPriority.HIGH));
        Instant now = Instant.now();

        AiTasksGenerated event = new AiTasksGenerated(eventId, "proj-1", tasks, "tenant-1", "user-1", now);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.projectId()).isEqualTo("proj-1");
        assertThat(event.tasks()).hasSize(1);
        assertThat(event.tasks().get(0).title()).isEqualTo("Deploy service");
        assertThat(event.tenantId()).isEqualTo("tenant-1");
        assertThat(event.generatedBy()).isEqualTo("user-1");
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    void construction_withNullEventId_generatesEventId() {
        List<TaskDraft> tasks = List.of(new TaskDraft("Task A", "Description", TaskPriority.MEDIUM));

        AiTasksGenerated event = new AiTasksGenerated(null, "proj-2", tasks, "tenant-2", "user-2", Instant.now());

        assertThat(event.eventId()).isNotNull();
    }

    @Test
    void construction_withNullOccurredAt_generatesTimestamp() {
        List<TaskDraft> tasks = List.of(new TaskDraft("Task B", "Description", TaskPriority.LOW));

        AiTasksGenerated event = new AiTasksGenerated(UUID.randomUUID(), "proj-3", tasks, "tenant-3", "user-3", null);

        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.occurredAt()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    void construction_withMultipleTasks_storesAll() {
        List<TaskDraft> tasks = List.of(
                new TaskDraft("Task 1", "Desc 1", TaskPriority.HIGH),
                new TaskDraft("Task 2", "Desc 2", TaskPriority.MEDIUM),
                new TaskDraft("Task 3", "Desc 3", TaskPriority.LOW)
        );

        AiTasksGenerated event = new AiTasksGenerated(UUID.randomUUID(), "proj-4", tasks, "t4", "u4", Instant.now());

        assertThat(event.tasks()).hasSize(3);
        assertThat(event.tasks().get(1).priority()).isEqualTo(TaskPriority.MEDIUM);
    }
}
