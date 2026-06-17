package com.epm.ai.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for {@link KafkaOutboxPublisher}.
 *
 * <p>Regression guards for H2 (synchronous publish + transactional status update)
 * and H3 (attempt counter increment). On success the row must be marked published;
 * on failure the row must be marked failed; every attempt increments {@code attempts}.
 */
@ExtendWith(MockitoExtension.class)
class KafkaOutboxPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxEventJpaRepository outboxRepo;

    @InjectMocks
    private KafkaOutboxPublisher publisher;

    // ── success: row marked published + attempts incremented ──────────────────

    @Test
    void publish_marksPublishedAndIncrementsAttempts_onSuccess() {
        OutboxEventJpaEntity entity = buildRow();
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        publisher.publish(entity);

        ArgumentCaptor<OutboxEventJpaEntity> captor =
                ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        OutboxEventJpaEntity saved = captor.getValue();
        assertThat(saved.getPublishedAt()).as("publishedAt must be set on success").isNotNull();
        assertThat(saved.getFailedAt()).isNull();
        assertThat(saved.getAttempts()).as("attempts must increment on each publish").isEqualTo(1);
    }

    // ── failure: row marked failed + attempts incremented ─────────────────────

    @Test
    void publish_marksFailedAndIncrementsAttempts_onFailure() {
        OutboxEventJpaEntity entity = buildRow();
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        publisher.publish(entity);

        ArgumentCaptor<OutboxEventJpaEntity> captor =
                ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(outboxRepo).save(captor.capture());
        OutboxEventJpaEntity saved = captor.getValue();
        assertThat(saved.getFailedAt()).as("failedAt must be set on failure").isNotNull();
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.getAttempts()).as("attempts must increment even on failure").isEqualTo(1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private OutboxEventJpaEntity buildRow() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateId(UUID.randomUUID());
        entity.setAggregateType("AiEvent");
        entity.setEventType("AiTasksGenerated");
        entity.setTopic("ai.events");
        entity.setPayload("{\"eventId\":\"test\"}");
        entity.setCreatedAt(Instant.now());
        return entity;
    }
}
