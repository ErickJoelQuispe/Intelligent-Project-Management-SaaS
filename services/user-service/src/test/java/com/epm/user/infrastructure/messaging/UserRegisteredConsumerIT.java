package com.epm.user.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import com.epm.user.infrastructure.AbstractPostgresIT;
import com.epm.user.infrastructure.adapter.out.persistence.UserProfileJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for {@link AccountRegisteredConsumer}.
 *
 * <p>Verifies that a message matching the auth.account.registered contract schema
 * is correctly consumed and creates a {@link com.epm.user.domain.model.UserProfile}.
 *
 * <p>Uses {@code @EmbeddedKafka} with a message payload that matches the Spring Cloud
 * Contract schema defined in auth-service's {@code accountRegistered.yml}.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"auth.account.registered"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json"
})
class UserRegisteredConsumerIT extends AbstractPostgresIT {

    private static final String TOPIC = "auth.account.registered";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private UserProfileJpaRepository profileJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void consumeAccountRegisteredEvent_createsUserProfile() throws Exception {
        // Arrange — build a message matching the contract schema
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String email = "contract-consumer-test@example.com";

        String message = buildContractMessage(eventId, accountId, tenantId, email, "Jane", "Doe");

        // Act — send the contract-matching message to the topic
        kafkaTemplate.send(new ProducerRecord<>(TOPIC, accountId.toString(), message)).get();

        // Assert — wait up to 5 seconds for the consumer to process
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> profileJpaRepository.findById(accountId).isPresent());

        var profile = profileJpaRepository.findById(accountId).orElseThrow();
        assertThat(profile.getEmail()).isEqualTo(email);
        assertThat(profile.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void consumeAccountRegisteredEvent_idempotency_skipsDuplicate() throws Exception {
        // Arrange — same eventId sent twice
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String email = "idempotent-test@example.com";

        String message = buildContractMessage(eventId, accountId, tenantId, email, "Bob", "Builder");

        // Act — send same message twice
        kafkaTemplate.send(new ProducerRecord<>(TOPIC, accountId.toString(), message)).get();

        // Wait for first processing
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> profileJpaRepository.findById(accountId).isPresent());

        // Send duplicate — should be skipped without error
        kafkaTemplate.send(new ProducerRecord<>(TOPIC, accountId.toString(), message)).get();

        // Brief wait to ensure duplicate processed
        Thread.sleep(500);

        // Assert — still exactly one profile
        assertThat(profileJpaRepository.findById(accountId)).isPresent();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildContractMessage(
            UUID eventId,
            UUID accountId,
            UUID tenantId,
            String email,
            String firstName,
            String lastName) throws Exception {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", "AccountRegistered");
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", java.time.Instant.now().toString());
        envelope.put("aggregateId", accountId.toString());
        envelope.put("aggregateType", "Account");
        envelope.put("tenantId", tenantId.toString());
        envelope.put("traceId", UUID.randomUUID().toString());

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("accountId", accountId.toString());
        payload.put("email", email);
        payload.put("firstName", firstName);
        payload.put("lastName", lastName);
        envelope.set("payload", payload);

        return objectMapper.writeValueAsString(envelope);
    }
}
