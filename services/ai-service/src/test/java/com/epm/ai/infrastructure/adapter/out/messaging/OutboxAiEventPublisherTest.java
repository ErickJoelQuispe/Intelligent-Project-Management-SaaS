package com.epm.ai.infrastructure.adapter.out.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.List;

import com.epm.ai.domain.event.AiTasksGenerated;
import com.epm.ai.domain.model.TaskDraft;
import com.epm.ai.domain.model.TaskPriority;
import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link OutboxAiEventPublisher}.
 * Verifies that AiTasksGenerated events are persisted to the outbox.
 * Uses a real ObjectMapper to avoid NPE in JSON building.
 */
@ExtendWith(MockitoExtension.class)
class OutboxAiEventPublisherTest {

    @Mock
    private OutboxEventJpaRepository outboxRepo;

    @Mock
    private ApplicationEventPublisher appEventPublisher;

    private OutboxAiEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxAiEventPublisher(outboxRepo, appEventPublisher, new ObjectMapper());
    }

    // ── publish saves to outbox repo ─────────────────────────────────────────

    @Test
    void publish_savesOutboxEntity_forAiTasksGeneratedEvent() throws Exception {
        List<TaskDraft> tasks = List.of(
                new TaskDraft("Task 1", "Description 1", TaskPriority.HIGH));
        AiTasksGenerated event = new AiTasksGenerated(null, "proj-1", tasks, "tenant-1", "user-1", null);

        publisher.publish(event);

        verify(outboxRepo).save(any());
    }

    // ── publish fires ApplicationEvent after save ─────────────────────────────

    @Test
    void publish_firesOutboxEventSavedEvent_afterSave() throws Exception {
        List<TaskDraft> tasks = List.of(
                new TaskDraft("DB Schema", "Create tables", TaskPriority.HIGH),
                new TaskDraft("API Routes", "Set up REST endpoints", TaskPriority.MEDIUM));
        AiTasksGenerated event = new AiTasksGenerated(null, "proj-2", tasks, "tenant-2", "user-2", null);

        publisher.publish(event);

        verify(appEventPublisher).publishEvent(any(OutboxEventSavedEvent.class));
    }
}
