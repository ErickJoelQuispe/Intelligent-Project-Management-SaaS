package com.epm.user.infrastructure.adapter.in.messaging;

import java.time.Instant;

import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.out.UserProfileRepository;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaEntity;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@code auth.account.registered} events.
 *
 * <p>Creates a {@link UserProfile} when an account is registered in auth-service.
 * Uses the processed_events table for idempotency.
 */
@Component
public class AccountRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountRegisteredConsumer.class);
    private static final String TOPIC = "auth.account.registered";

    private final UserProfileRepository profileRepository;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public AccountRegisteredConsumer(UserProfileRepository profileRepository,
            ProcessedEventJpaRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "user-service")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.get("eventId").asText();

            // Idempotency check
            if (processedEventRepository.existsByEventId(eventId)) {
                log.debug("Skipping duplicate event: {}", eventId);
                return;
            }

            // Extract payload
            JsonNode payload = root.get("payload");
            String accountIdStr = payload.get("accountId").asText();
            String tenantIdStr = payload.path("tenantId").asText(root.get("tenantId").asText());
            String email = payload.get("email").asText();
            String firstName = payload.path("firstName").asText("Unknown");
            String lastName = payload.path("lastName").asText("Unknown");

            java.util.UUID accountId = java.util.UUID.fromString(accountIdStr);
            java.util.UUID tenantId = java.util.UUID.fromString(tenantIdStr);

            // Create profile
            UserProfile profile = UserProfile.create(accountId, tenantId, email, firstName, lastName);
            profileRepository.save(profile);

            // Mark as processed
            processedEventRepository.save(new ProcessedEventJpaEntity(eventId, TOPIC, Instant.now()));

            log.info("Created UserProfile for account: {}", accountId);
        } catch (Exception e) {
            log.error("Failed to process auth.account.registered event", e);
            throw new RuntimeException("Failed to process auth.account.registered event", e);
        }
    }
}
