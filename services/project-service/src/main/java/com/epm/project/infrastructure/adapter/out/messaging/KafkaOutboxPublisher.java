package com.epm.project.infrastructure.adapter.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends a single outbox event payload to Kafka.
 *
 * <p>This class is concerned ONLY with Kafka delivery. It does NOT mutate the
 * outbox row — that is the responsibility of {@link OutboxRelayExecutor}, which
 * calls this publisher and then updates {@code published_at} or {@code failed_at}
 * based on the outcome.
 */
@Component
public class KafkaOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes the given payload to the specified Kafka topic using the aggregate ID as
     * the partition key.
     *
     * @param topic       the Kafka topic to publish to
     * @param partitionKey the message key (typically the aggregate ID as a string)
     * @param payload     the serialized event payload
     * @throws RuntimeException if Kafka send fails synchronously
     */
    public void publish(String topic, String partitionKey, String payload) {
        log.debug("Publishing to topic={} key={}", topic, partitionKey);
        kafkaTemplate.send(topic, partitionKey, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Kafka publish failed topic={} key={}: {}", topic, partitionKey, ex.getMessage());
                throw new RuntimeException("Kafka publish failed: " + ex.getMessage(), ex);
            }
        });
    }
}
