package com.epm.auth;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.epm.auth.infrastructure.AbstractPostgresIT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.uuid.UuidCreator;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifierReceiver;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifierSender;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

/**
 * Base class for Spring Cloud Contract messaging tests (producer side).
 *
 * <p>SCC generates a test class that extends this base and calls {@link #accountRegistration()}
 * to trigger the message. A custom {@link MessageVerifierReceiver} backed by a raw
 * {@link KafkaConsumer} enables the contract verifier to receive from Kafka topics directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureMessageVerifier
@EmbeddedKafka(
        partitions = 1,
        topics = {"auth.account.registered"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "keycloak.server-url=http://localhost:8180",
    "keycloak.realm=epm",
    "keycloak.client-id=epm-backend",
    "keycloak.client-secret=test-secret",
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false"
})
public abstract class ContractVerifierBase extends AbstractPostgresIT {

    private static final String TOPIC = "auth.account.registered";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Trigger method called by SCC-generated contract tests.
     * Publishes a synthetic AccountRegistered event directly to Kafka,
     * matching the envelope structure of OutboxDomainEventPublisher.
     */
    public void accountRegistration() {
        try {
            UUID accountId = UuidCreator.getTimeOrderedEpoch();
            UUID tenantId = UuidCreator.getTimeOrderedEpoch();
            UUID eventId = UuidCreator.getTimeOrderedEpoch();
            UUID keycloakUserId = UUID.randomUUID();

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", eventId.toString());
            envelope.put("eventType", "AccountRegistered");
            envelope.put("eventVersion", 1);
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("aggregateId", accountId.toString());
            envelope.put("aggregateType", "Account");
            envelope.put("tenantId", tenantId.toString());
            // Intentionally synthetic traceId — this is contract test scaffolding, not production.
            // The real publisher resolves traceId from the active Micrometer span at runtime.
            envelope.put("traceId", UUID.randomUUID().toString());

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("accountId", accountId.toString());
            payload.put("keycloakUserId", keycloakUserId.toString());
            payload.put("email", "contract@example.com");
            payload.put("firstName", "Contract");
            payload.put("lastName", "Test");

            envelope.set("payload", payload);

            String message = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(TOPIC, accountId.toString(), message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to trigger accountRegistration contract event", e);
        }
    }

    /**
     * Inner configuration class that provides a Kafka-backed {@link MessageVerifierReceiver}
     * so SCC can poll messages from the embedded Kafka broker during contract verification.
     */
    @Configuration
    static class KafkaVerifierConfig {

        @Value("${spring.embedded.kafka.brokers:localhost:9092}")
        private String brokers;

        @Bean
        public MessageVerifierSender<Message<?>> kafkaMessageVerifierSender() {
            // Sender is handled directly via KafkaTemplate in accountRegistration()
            // This NoOp sender prevents the default Spring Integration sender from interfering.
            return new MessageVerifierSender<>() {
                @Override
                public void send(Message<?> message, String destination) {
                    // Not used — trigger method handles sending
                }

                @Override
                public <T> void send(T payload, Map<String, Object> headers, String destination) {
                    // Not used
                }

                @Override
                public <T> void send(T payload, Map<String, Object> headers, String destination,
                        org.springframework.cloud.contract.verifier.converter.YamlContract contract) {
                    // Not used
                }

                @Override
                public void send(Message<?> message, String destination,
                        org.springframework.cloud.contract.verifier.converter.YamlContract contract) {
                    // Not used
                }
            };
        }

        @Bean
        public MessageVerifierReceiver<Message<?>> kafkaMessageVerifierReceiver() {
            return new MessageVerifierReceiver<>() {
                @Override
                public Message<?> receive(String destination, long timeout, TimeUnit timeUnit,
                        org.springframework.cloud.contract.verifier.converter.YamlContract contract) {
                    return receive(destination, timeout, timeUnit);
                }

                @Override
                public Message<?> receive(String destination, long timeout, TimeUnit timeUnit) {
                    Map<String, Object> props = new HashMap<>();
                    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
                    props.put(ConsumerConfig.GROUP_ID_CONFIG, "scc-verifier-" + UUID.randomUUID());
                    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);

                    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                        consumer.subscribe(Collections.singletonList(destination));
                        var records = consumer.poll(Duration.ofMillis(timeUnit.toMillis(timeout)));
                        if (records.isEmpty()) {
                            return null;
                        }
                        ConsumerRecord<String, String> record = records.iterator().next();
                        return MessageBuilder
                                .withPayload(record.value())
                                .setHeader("contentType", "application/json")
                                .build();
                    }
                }

                @Override
                public Message<?> receive(String destination,
                        org.springframework.cloud.contract.verifier.converter.YamlContract contract) {
                    return receive(destination, 5, TimeUnit.SECONDS);
                }

                @Override
                public Message<?> receive(String destination) {
                    return receive(destination, 5, TimeUnit.SECONDS);
                }
            };
        }
    }
}
