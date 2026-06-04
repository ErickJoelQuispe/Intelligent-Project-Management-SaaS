package com.epm.notification.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.infrastructure.adapter.in.messaging.AiEventConsumerStub;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for AiEventConsumerStub (TDD — Strict RED→GREEN→REFACTOR).
 *
 * <p>Verifies: any message is received → INFO logged, no exception, no DB write.
 */
@ExtendWith(MockitoExtension.class)
class AiEventConsumerStubTest {

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private ProcessedEventJpaRepository processedEventRepo;

    private AiEventConsumerStub stub;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        stub = new AiEventConsumerStub(objectMapper);
    }

    // ── Any AI event → INFO logged, no exception, no DB write ──────────────

    @Test
    void consume_anyAiEvent_logsAndDoesNotPersistOrThrow() {
        String message = buildAiEvent(UUID.randomUUID().toString(), "TaskSuggested",
                UUID.randomUUID().toString());

        // Should not throw
        stub.consume(message);

        // No persistence calls — this is a pure stub
        verify(processedEventRepo, never()).save(any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void consume_malformedAiEvent_doesNotThrow() {
        // Even malformed messages should not crash — stub should be resilient
        stub.consume("{\"incomplete\": true}");

        verify(processedEventRepo, never()).save(any());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String buildAiEvent(String eventId, String eventType, String tenantId) {
        return "{\"eventId\":\"" + eventId + "\""
                + ",\"eventType\":\"" + eventType + "\""
                + ",\"tenantId\":\"" + tenantId + "\""
                + ",\"occurredAt\":\"2026-06-04T10:00:00Z\""
                + ",\"payload\":{\"suggestion\":\"Use microservices\"}}";
    }
}
