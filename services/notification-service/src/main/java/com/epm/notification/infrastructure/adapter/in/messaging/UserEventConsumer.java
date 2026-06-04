package com.epm.notification.infrastructure.adapter.in.messaging;

import java.time.Instant;
import java.util.UUID;

import com.epm.notification.application.usecase.NotificationApplicationService;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.in.CacheUserEmailUseCase;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.notification.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code user.events} topic.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@code UserRegistered} → populates {@code user_email_cache} via {@link CacheUserEmailUseCase}</li>
 *   <li>{@code MemberJoinedTeam} → creates {@code MEMBER_JOINED_TEAM} notification</li>
 *   <li>{@code MemberLeftTeam} → creates {@code MEMBER_LEFT_TEAM} notification</li>
 * </ul>
 *
 * <p>Uses {@code processed_events} for idempotency. Unknown event types are logged at WARN and skipped.
 */
@Component
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);
    private static final String TOPIC = "user.events";

    private final NotificationApplicationService notificationService;
    private final CacheUserEmailUseCase cacheUserEmailUseCase;
    private final ProcessedEventJpaRepository processedEventRepo;
    private final ObjectMapper objectMapper;

    public UserEventConsumer(NotificationApplicationService notificationService,
            CacheUserEmailUseCase cacheUserEmailUseCase,
            ProcessedEventJpaRepository processedEventRepo,
            ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.cacheUserEmailUseCase = cacheUserEmailUseCase;
        this.processedEventRepo = processedEventRepo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "notification-group")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.get("eventId").asText();

            // Idempotency check
            if (processedEventRepo.existsByEventId(eventId)) {
                log.warn("Skipping duplicate user event: eventId={}", eventId);
                return;
            }

            String eventType = root.get("eventType").asText();
            UUID tenantId = UUID.fromString(root.get("tenantId").asText());
            JsonNode payload = root.get("payload");
            UUID userId = UUID.fromString(payload.get("userId").asText());

            dispatch(eventType, tenantId, userId, payload);

            processedEventRepo.save(new ProcessedEventJpaEntity(eventId, TOPIC, Instant.now()));
            log.info("Processed user event: eventId={}, type={}", eventId, eventType);

        } catch (Exception e) {
            log.error("Failed to process user event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process user event", e);
        }
    }

    private void dispatch(String eventType, UUID tenantId, UUID userId, JsonNode payload) {
        switch (eventType) {
            case "UserRegistered" -> {
                String email = payload.get("email").asText();
                cacheUserEmailUseCase.cacheUserEmail(userId, tenantId, email);
            }
            case "MemberJoinedTeam" -> {
                String teamName = textOrDefault(payload, "teamName", "a team");
                notificationService.create(tenantId, userId,
                        NotificationType.MEMBER_JOINED_TEAM, userId,
                        "You joined " + teamName);
            }
            case "MemberLeftTeam" -> {
                String teamName = textOrDefault(payload, "teamName", "a team");
                notificationService.create(tenantId, userId,
                        NotificationType.MEMBER_LEFT_TEAM, userId,
                        "You left " + teamName);
            }
            default -> log.warn("Unknown user event type — skipping: eventType={}", eventType);
        }
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}
