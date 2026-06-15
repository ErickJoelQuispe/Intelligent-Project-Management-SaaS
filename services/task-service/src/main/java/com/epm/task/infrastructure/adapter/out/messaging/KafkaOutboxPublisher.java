package com.epm.task.infrastructure.adapter.out.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.task.infrastructure.messaging.tracing.KafkaTracingSupport;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes a single outbox event to Kafka — blocking send.
 *
 * <p>Injects W3C trace context headers ({@code traceparent}) into each outgoing
 * {@link ProducerRecord} via {@link KafkaTracingSupport} so that downstream consumers
 * can continue the distributed trace.
 *
 * <p><strong>Blocking send</strong>: the send is blocking ({@code .get(timeout)}).
 * With an async {@code whenComplete} callback the relay would update the outbox row on
 * the producer thread — outside the relay's {@code @Transactional} boundary — so a failed
 * send would not reliably set {@code failed_at} within the relay transaction.
 * Blocking keeps the success/failure determination synchronous and within the relay's
 * transaction boundary, where {@link OutboxRelayExecutor} owns the DB state update.
 *
 * <p><strong>Responsibility split</strong>: this class is responsible for Kafka delivery only.
 * Setting {@code published_at} or {@code failed_at} on the outbox entity is done by
 * {@link OutboxRelayExecutor} after this method returns or throws.
 */
@Component
public class KafkaOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends the event payload to Kafka, blocking until the broker acknowledges.
     *
     * <p>Builds a {@link ProducerRecord} explicitly so that W3C trace headers can be
     * injected before the record is handed to the Kafka producer.
     *
     * @param entity the outbox event to publish
     * @throws RuntimeException if the send fails, times out, or the thread is interrupted
     */
    public void publish(OutboxEventJpaEntity entity) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                entity.getTopic(), entity.getAggregateId().toString(), entity.getPayload());
        KafkaTracingSupport.injectTraceHeaders(record);

        try {
            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted publishing outbox event {} to topic {}",
                    entity.getId(), entity.getTopic(), e);
            throw new RuntimeException("Kafka send interrupted for event " + entity.getId(), e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Failed to publish outbox event {} to topic {}",
                    entity.getId(), entity.getTopic(), e);
            throw new RuntimeException("Kafka send failed for event " + entity.getId(), e);
        }
    }
}
