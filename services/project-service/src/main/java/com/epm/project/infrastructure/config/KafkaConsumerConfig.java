package com.epm.project.infrastructure.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer error handling configuration.
 *
 * <p>Uses {@link DefaultErrorHandler} with {@link FixedBackOff} (3 retries, 500ms interval)
 * and a {@link DeadLetterPublishingRecoverer} that sends failed messages to
 * {@code user.team.deleted.DLT}.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", 0));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3L));
    }
}
