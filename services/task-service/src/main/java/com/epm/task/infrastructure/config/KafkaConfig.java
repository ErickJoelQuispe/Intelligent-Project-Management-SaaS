package com.epm.task.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka producer and consumer error handling configuration for task-service.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Maximum time {@code KafkaProducer.send()} may block while waiting for metadata/buffer.
     * Bounded so that a down or unreachable broker fails the relay's blocking send quickly
     * instead of hanging the relay thread for the Kafka default of 60 000 ms. The outbox
     * relay simply marks the row failed and retries on the next poll, so a short block is safe.
     */
    @Value("${task.kafka.producer.max-block-ms:5000}")
    private int maxBlockMs;

    /**
     * Upper bound on the total time to report success/failure of a single send (includes
     * retries). Kept aligned with {@link #maxBlockMs} so a dead broker surfaces a failure
     * promptly rather than tying up the relay.
     */
    @Value("${task.kafka.producer.delivery-timeout-ms:5000}")
    private int deliveryTimeoutMs;

    @Bean
    ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Bound the time a send can block on metadata so a dead/slow broker cannot pin the
        // outbox-relay thread for the Kafka default of 60s (delivery.timeout must be >=
        // request.timeout + linger; request.timeout default 30s is lowered to fit).
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", 0));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3L));
    }
}
