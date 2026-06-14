package com.epm.project.infrastructure.adapter.out.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
 *
 * <p>The send is <strong>blocking</strong> ({@code .get(timeout)}): the relay's
 * synchronous try/catch must observe broker failures. If we returned before the broker
 * acked (e.g. via an async {@code whenComplete} callback on the producer thread), the
 * relay would mark the row {@code published_at} even when the send actually failed.
 * Blocking surfaces the failure to {@link OutboxRelayExecutor} so it sets {@code failed_at}.
 */
@Component
public class KafkaOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes the given payload to the specified Kafka topic using the aggregate ID as
     * the partition key, blocking until the broker acknowledges (or the send fails/times out).
     *
     * @param topic        the Kafka topic to publish to
     * @param partitionKey the message key (typically the aggregate ID as a string)
     * @param payload      the serialized event payload
     * @throws RuntimeException if the send fails, times out, or the thread is interrupted
     */
    public void publish(String topic, String partitionKey, String payload) {
        log.debug("Publishing to topic={} key={}", topic, partitionKey);
        try {
            kafkaTemplate.send(topic, partitionKey, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Kafka publish interrupted for topic: " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Kafka publish failed topic={} key={}: {}", topic, partitionKey, e.getMessage());
            throw new RuntimeException("Kafka publish failed for topic: " + topic, e);
        }
    }
}
