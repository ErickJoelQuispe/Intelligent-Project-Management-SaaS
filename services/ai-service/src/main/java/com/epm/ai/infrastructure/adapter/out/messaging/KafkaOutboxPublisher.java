package com.epm.ai.infrastructure.adapter.out.messaging;

import java.time.Instant;

import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.ai.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes a single outbox event to Kafka and marks it as published.
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
     * @param entity the outbox event to publish
     */
    public void publish(OutboxEventJpaEntity entity) {
        try {
            kafkaTemplate.send(entity.getTopic(), entity.getAggregateId().toString(), entity.getPayload())
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
