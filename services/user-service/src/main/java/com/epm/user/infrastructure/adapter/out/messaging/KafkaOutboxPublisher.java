package com.epm.user.infrastructure.adapter.out.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox events to Kafka synchronously.
 *
 * <p>Uses a blocking send to guarantee the message was accepted by the broker
 * before marking the outbox row as published.
 */
@Component
public class KafkaOutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a message to the given Kafka topic synchronously.
     *
     * @param topic   Kafka topic name
     * @param key     message key (typically the aggregate ID)
     * @param payload message payload (JSON string)
     * @throws RuntimeException if the send fails or times out
     */
    public void publish(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Kafka publish interrupted for topic: " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("Kafka publish failed for topic: " + topic, e);
        }
    }
}
