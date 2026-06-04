package com.epm.notification.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Stub Kafka consumer for the {@code ai.events} topic.
 *
 * <p>This is a STUB — it receives messages and logs them at INFO level only.
 * No persistence, no notification creation. The topic may not exist in all environments.
 *
 * <p>Uses {@code missingTopicsFatal = "false"} so the service starts even when
 * the {@code ai.events} topic has not been created yet.
 */
@Component
public class AiEventConsumerStub {

    private static final Logger log = LoggerFactory.getLogger(AiEventConsumerStub.class);

    private final ObjectMapper objectMapper;

    public AiEventConsumerStub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "ai.events",
            groupId = "notification-group",
            containerFactory = "aiEventsContainerFactory")
    public void consume(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.has("eventType") ? root.get("eventType").asText() : "unknown";
            String eventId = root.has("eventId") ? root.get("eventId").asText() : "unknown";
            log.info("AI event received (stub): type={}, id={}", eventType, eventId);
        } catch (Exception e) {
            log.warn("AI event received but could not be parsed (stub): {}", e.getMessage());
        }
        // No persistence — intentional stub behavior
    }
}
