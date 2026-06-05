package com.epm.task.infrastructure.kafka;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.port.in.CreateTaskUseCase;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.infrastructure.adapter.in.messaging.AiEventConsumer;
import com.epm.task.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AiEventConsumer}.
 *
 * <p>Uses Mockito only — no Spring ApplicationContext required.
 */
@ExtendWith(MockitoExtension.class)
class AiEventConsumerTest {

    @Mock
    private CreateTaskUseCase createTaskUseCase;

    @Mock
    private ProcessedEventJpaRepository processedEventRepo;

    private AiEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AiEventConsumer(createTaskUseCase, processedEventRepo);
    }

    // ── Scenario: valid batch creates N tasks ──────────────────────────────────

    @Test
    void consume_validBatch_createsOneTaskPerDraft() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        String message = buildAiTasksGeneratedMessage(eventId, projectId, tenantId, generatedBy,
                List.of(
                        new TaskDraftSpec("Implement login", "Add JWT login endpoint", "HIGH"),
                        new TaskDraftSpec("Write unit tests", "Cover auth module", "MEDIUM"),
                        new TaskDraftSpec("Setup CI", "Configure GitHub Actions", "LOW")
                ));

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);

        consumer.consume(message);

        ArgumentCaptor<CreateTaskCommand> captor = forClass(CreateTaskCommand.class);
        verify(createTaskUseCase, times(3)).execute(captor.capture());

        List<CreateTaskCommand> commands = captor.getAllValues();
        assertThat(commands).hasSize(3);

        // Verify each command carries the correct projectId and tenantId
        assertThat(commands).allSatisfy(cmd -> {
            assertThat(cmd.projectId()).isEqualTo(projectId);
            assertThat(cmd.tenantId()).isEqualTo(tenantId);
            assertThat(cmd.callerId()).isEqualTo(generatedBy);
        });

        // Verify titles are mapped in order
        assertThat(commands.get(0).title()).isEqualTo("Implement login");
        assertThat(commands.get(0).priority()).isEqualTo(TaskPriority.HIGH);

        assertThat(commands.get(1).title()).isEqualTo("Write unit tests");
        assertThat(commands.get(1).priority()).isEqualTo(TaskPriority.MEDIUM);

        assertThat(commands.get(2).title()).isEqualTo("Setup CI");
        assertThat(commands.get(2).priority()).isEqualTo(TaskPriority.LOW);
    }

    // ── Triangulation: single-task batch ──────────────────────────────────────

    @Test
    void consume_singleTaskBatch_createsExactlyOneTask() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        String message = buildAiTasksGeneratedMessage(eventId, projectId, tenantId, generatedBy,
                List.of(new TaskDraftSpec("Deploy to prod", "Blue-green deployment", "HIGH")));

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(false);

        consumer.consume(message);

        ArgumentCaptor<CreateTaskCommand> captor = forClass(CreateTaskCommand.class);
        verify(createTaskUseCase, times(1)).execute(captor.capture());

        CreateTaskCommand cmd = captor.getValue();
        assertThat(cmd.title()).isEqualTo("Deploy to prod");
        assertThat(cmd.description()).isEqualTo("Blue-green deployment");
        assertThat(cmd.priority()).isEqualTo(TaskPriority.HIGH);
    }

    // ── Scenario: duplicate event is skipped ──────────────────────────────────

    @Test
    void consume_duplicateEvent_skipsProcessing() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        String message = buildAiTasksGeneratedMessage(eventId, projectId, tenantId, generatedBy,
                List.of(new TaskDraftSpec("Some task", "Some description", "MEDIUM")));

        when(processedEventRepo.existsByEventId(eventId.toString())).thenReturn(true);

        consumer.consume(message);

        verify(createTaskUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    // ── Scenario: malformed JSON does not crash the consumer ──────────────────

    @Test
    void consume_malformedJson_doesNotCrashConsumer() {
        String malformedMessage = "{ not valid json at all !!!";

        // Must not throw — consumer must handle gracefully
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> consumer.consume(malformedMessage));

        verify(createTaskUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private record TaskDraftSpec(String title, String description, String priority) {}

    private String buildAiTasksGeneratedMessage(
            UUID eventId,
            UUID projectId,
            UUID tenantId,
            UUID generatedBy,
            List<TaskDraftSpec> drafts) {

        StringBuilder tasksJson = new StringBuilder("[");
        for (int i = 0; i < drafts.size(); i++) {
            TaskDraftSpec d = drafts.get(i);
            tasksJson.append("""
                    {"title":"%s","description":"%s","priority":"%s"}"""
                    .formatted(d.title(), d.description(), d.priority()));
            if (i < drafts.size() - 1) {
                tasksJson.append(",");
            }
        }
        tasksJson.append("]");

        return """
                {
                  "eventId": "%s",
                  "eventType": "AiTasksGenerated",
                  "tenantId": "%s",
                  "occurredAt": "2026-06-05T12:00:00Z",
                  "payload": {
                    "projectId": "%s",
                    "generatedBy": "%s",
                    "tasks": %s
                  }
                }
                """.formatted(eventId, tenantId, projectId, generatedBy, tasksJson);
    }
}
