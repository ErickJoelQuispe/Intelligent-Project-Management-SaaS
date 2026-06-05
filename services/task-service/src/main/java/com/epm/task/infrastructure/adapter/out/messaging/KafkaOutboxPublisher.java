package com.epm.task.infrastructure.adapter.out.messaging;

import java.time.Instant;

import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.task.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.epm.task.infrastructure.messaging.tracing.KafkaTracingSupport;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes a single outbox event to Kafka and marks it as published.
 *
 * <p>Injects W3C trace context headers ({@code traceparent}) into each outgoing
 * {@link ProducerRecord} via {@link KafkaTracingSupport} so that downstream consumers
 * can continue the distributed trace.
 */
@Component
public class KafkaOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventJpaRepository outboxRepo;

    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate,
            OutboxEventJpaRepository outboxRepo) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxRepo = outboxRepo;
    }

    /**
     * Sends the event payload to Kafka and updates the outbox row.
     *
     * <p>Builds a {@link ProducerRecord} explicitly so that W3C trace headers can be
     * injected before the record is handed to the Kafka producer.
     *
     * @param entity the outbox event to publish
     */
    public void publish(OutboxEventJpaEntity entity) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    entity.getTopic(), entity.getAggregateId().toString(), entity.getPayload());
            KafkaTracingSupport.injectTraceHeaders(record);

            kafkaTemplate.send(record)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish outbox event {} to topic {}",
                                    entity.getId(), entity.getTopic(), ex);
                            entity.setFailedAt(Instant.now());
                            entity.setError(ex.getMessage());
                        } else {
                            entity.setPublishedAt(Instant.now());
                        }
                        outboxRepo.save(entity);
                    });
        } catch (Exception ex) {
            log.error("Exception publishing outbox event {}", entity.getId(), ex);
            entity.setFailedAt(Instant.now());
            entity.setError(ex.getMessage());
            outboxRepo.save(entity);
        }
    }
}
