package com.epm.task.infrastructure.kafka;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import com.epm.task.domain.model.TaskPriority;
import com.epm.task.domain.port.in.CreateTaskUseCase;
import com.epm.task.domain.port.in.command.CreateTaskCommand;
import com.epm.task.infrastructure.adapter.in.messaging.AiEventConsumer;
import com.epm.task.infrastructure.adapter.in.messaging.IdempotencyGuard;
import com.epm.task.infrastructure.adapter.in.messaging.ProcessedEventClaimer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
    private IdempotencyGuard idempotencyGuard;

    @Mock
    private ProcessedEventClaimer processedEventClaimer;

    private AiEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new AiEventConsumer(createTaskUseCase, idempotencyGuard, processedEventClaimer, objectMapper);
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

        when(idempotencyGuard.claim(eventId.toString(), "ai.events")).thenReturn(true);

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

        when(idempotencyGuard.claim(eventId.toString(), "ai.events")).thenReturn(true);

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

        when(idempotencyGuard.claim(eventId.toString(), "ai.events")).thenReturn(false);

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

    // ── DoS guard: batch size exceeds max ─────────────────────────────────────

    @Test
    void consume_batchExceedsMaxSize_discardedAsPoison() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        // Build 51 drafts — one over the limit
        List<TaskDraftSpec> drafts = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(i -> new TaskDraftSpec("Task " + i, "Desc " + i, "MEDIUM"))
                .toList();

        String message = buildAiTasksGeneratedMessage(eventId, projectId, tenantId, generatedBy, drafts);

        consumer.consume(message);

        // Poisoned before claim — no tasks created, guard not called
        verify(idempotencyGuard, never()).claim(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(createTaskUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    // ── Draft validation: blank title skipped ─────────────────────────────────

    @Test
    void consume_draftWithBlankTitle_skipped_otherDraftsProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        // Build raw JSON with a blank-title draft + a valid draft
        String message = """
                {
                  "eventId": "%s",
                  "eventType": "AiTasksGenerated",
                  "tenantId": "%s",
                  "occurredAt": "2026-06-15T12:00:00Z",
                  "payload": {
                    "projectId": "%s",
                    "generatedBy": "%s",
                    "tasks": [
                      {"title": "  ", "description": "blank title", "priority": "MEDIUM"},
                      {"title": "Valid task", "description": "good", "priority": "LOW"}
                    ]
                  }
                }
                """.formatted(eventId, tenantId, projectId, generatedBy);

        when(idempotencyGuard.claim(eventId.toString(), "ai.events")).thenReturn(true);

        consumer.consume(message);

        // Only the valid draft should be created
        verify(createTaskUseCase, times(1)).execute(org.mockito.ArgumentMatchers.any());
    }

    // ── Scenario: DataIntegrityViolationException is a per-draft business skip ──
    //
    // DataIntegrityViolationException extends NonTransientDataAccessException — it is thrown
    // for DATA problems (a draft value violating a DB CHECK / length / not-null constraint).
    // It must NOT be classified as a transient infrastructure failure: doing so would release
    // the idempotency claim and rethrow, causing Kafka to redeliver, re-classify as infra
    // again, and spin in an infinite poison loop (re-creating the earlier drafts each cycle).
    // Instead it must be treated like any other per-draft business error: log + skip + continue.

    @Test
    void consume_dataIntegrityViolationOnOneDraft_isPerDraftSkip_noRethrow_claimNotReleased() {
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID generatedBy = UUID.randomUUID();

        String message = buildAiTasksGeneratedMessage(eventId, projectId, tenantId, generatedBy,
                List.of(
                        new TaskDraftSpec("Bad draft", "Violates a DB constraint", "MEDIUM"),
                        new TaskDraftSpec("Good draft", "Valid", "LOW")));

        when(idempotencyGuard.claim(eventId.toString(), "ai.events")).thenReturn(true);

        // First draft trips a DB constraint (non-transient); second draft succeeds.
        org.mockito.Mockito.doThrow(
                        new org.springframework.dao.DataIntegrityViolationException("CHECK constraint violated"))
                .doReturn(null)
                .when(createTaskUseCase).execute(org.mockito.ArgumentMatchers.any());

        // The consumer must NOT rethrow — a constraint violation is a per-draft business skip.
        assertThatCode(() -> consumer.consume(message))
                .as("DataIntegrityViolationException must be a per-draft skip, not an infra rethrow")
                .doesNotThrowAnyException();

        // Both drafts were attempted (batch continued past the failing draft).
        verify(createTaskUseCase, times(2)).execute(org.mockito.ArgumentMatchers.any());

        // The idempotency claim must NOT be released — this is not an infrastructure failure.
        verify(processedEventClaimer, never()).releaseClaim(org.mockito.ArgumentMatchers.any());
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
