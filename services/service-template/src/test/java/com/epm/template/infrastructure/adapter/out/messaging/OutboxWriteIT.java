package com.epm.template.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.epm.template.domain.model.Example;
import com.epm.template.domain.port.out.TransactionalExampleWriter;
import com.epm.template.infrastructure.AbstractPostgresIT;
import com.epm.template.infrastructure.adapter.out.persistence.ExampleJpaRepository;
import com.epm.template.infrastructure.adapter.out.persistence.OutboxEventJpaEntity;
import com.epm.template.infrastructure.adapter.out.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Positive outbox-write integration test against a REAL Testcontainers PostgreSQL database.
 *
 * <p>Unlike the negative atomicity test (which mocks the publisher to force a rollback), this
 * test uses the REAL {@code OutboxEventPublisherAdapter}, so the full envelope build + DB insert
 * path is exercised. It proves the happy path is atomic (exactly one examples row and one
 * outbox_events row) and that the persisted JSON payload carries all 8 envelope fields plus the
 * nested payload object.
 *
 * <p>{@link KafkaOutboxPublisher} is mocked because there is no real Kafka broker here — the
 * relay is irrelevant to this test, which asserts only the outbox WRITE, not the forward.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.task.scheduling.enabled=false"
})
class OutboxWriteIT extends AbstractPostgresIT {

    private static final String TOPIC_EXAMPLE_CREATED = "template.example.created";

    @Autowired
    private TransactionalExampleWriter writer;

    @Autowired
    private ExampleJpaRepository exampleJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KafkaOutboxPublisher kafkaOutboxPublisher;

    @AfterEach
    void cleanup() {
        outboxEventJpaRepository.deleteAll();
        exampleJpaRepository.deleteAll();
    }

    @Test
    void saveAndPublish_writesExactlyOneExampleAndOneEnvelopeRow() throws Exception {
        UUID tenantId = UUID.randomUUID();

        writer.saveAndPublish(Example.create(tenantId, "Write Test Example"));

        // Atomic happy path: exactly one row in each table.
        assertThat(exampleJpaRepository.findAll()).hasSize(1);
        assertThat(outboxEventJpaRepository.findAll()).hasSize(1);

        OutboxEventJpaEntity row = outboxEventJpaRepository.findAll().get(0);
        assertThat(row.getAggregateType()).isEqualTo("Example");
        assertThat(row.getEventType()).isEqualTo("ExampleCreated");
        assertThat(row.getTopic()).isEqualTo(TOPIC_EXAMPLE_CREATED);

        // The payload JSON parses and carries all 8 envelope fields + a nested payload object.
        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.hasNonNull("eventId")).isTrue();
        assertThat(envelope.get("eventType").asText()).isEqualTo("ExampleCreated");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.hasNonNull("occurredAt")).isTrue();
        assertThat(envelope.hasNonNull("aggregateId")).isTrue();
        assertThat(envelope.get("aggregateType").asText()).isEqualTo("Example");
        assertThat(envelope.get("tenantId").asText()).isEqualTo(tenantId.toString());
        assertThat(envelope.hasNonNull("traceId")).isTrue();

        JsonNode payload = envelope.get("payload");
        assertThat(payload).isNotNull();
        assertThat(payload.hasNonNull("exampleId")).isTrue();
        assertThat(payload.get("name").asText()).isEqualTo("Write Test Example");
    }
}
