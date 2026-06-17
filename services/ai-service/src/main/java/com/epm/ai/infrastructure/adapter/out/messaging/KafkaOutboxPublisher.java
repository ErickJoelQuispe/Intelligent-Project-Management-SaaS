package com.epm.ai.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes a single outbox event to Kafka and marks it as published.
 *
 * <p><b>H2 fix:</b> the Kafka send is performed <em>synchronously</em> (block with a
 * timeout) rather than via an async {@code whenComplete} callback. The old async
 * callback ran on a Kafka producer-IO thread with no transaction and a detached
 * entity, so {@code publishedAt} could silently fail to persist — leaving the row
 * pending and re-selected by the scheduler (double publish). Running the send and
 * the status update on the caller's thread keeps them inside the relay transaction.
 *
 * <p><b>H3 fix:</b> {@code attempts} is incremented on every call (success or
 * failure) so {@link OutboxRelayService} can park poison events after a cap.
 */
@Component
public class KafkaOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventJpaRepository outboxRepo;

    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                OutboxEventJpaRepository outboxRepo) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxRepo = outboxRepo;
    }

    /**
     * Synchronously sends the event payload to Kafka and updates the outbox row.
     *
     * <p>Must run inside the caller's transaction (see {@link OutboxRelayService})
     * so the status update is committed atomically with the SELECT that claimed it.
     *
     * @param entity the outbox event to publish
     */
    public void publish(OutboxEventJpaEntity entity) {
        // Count this attempt regardless of outcome (H3: poison-event cap).
        entity.setAttempts(entity.getAttempts() + 1);
        try {
            kafkaTemplate.send(entity.getTopic(), entity.getAggregateId().toString(), entity.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            entity.setPublishedAt(Instant.now());
            entity.setFailedAt(null);
            entity.setError(null);
        } catch (Exception ex) {
            log.error("Failed to publish outbox event {} to topic {} (attempt {})",
                    entity.getId(), entity.getTopic(), entity.getAttempts(), ex);
            entity.setFailedAt(Instant.now());
            // Unwrap ExecutionException to store the actual root-cause message.
            Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
            entity.setError(cause.getMessage());
            // Restore interrupt flag if we were interrupted while blocking.
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        outboxRepo.save(entity);
    }
}
