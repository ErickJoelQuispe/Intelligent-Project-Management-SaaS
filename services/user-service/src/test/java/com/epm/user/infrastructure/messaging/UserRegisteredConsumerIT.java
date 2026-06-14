package com.epm.user.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.epm.user.infrastructure.AbstractPostgresIT;
import com.epm.user.infrastructure.adapter.in.messaging.AccountRegisteredConsumer;
import com.epm.user.infrastructure.adapter.out.persistence.ProcessedEventJpaRepository;
import com.epm.user.infrastructure.adapter.out.persistence.UserProfileJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for {@code AccountRegisteredConsumer}.
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
    private ProcessedEventJpaRepository processedEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRegisteredConsumer consumer;

    @AfterEach
    void cleanup() {
        // Isolate tests: both tables must be empty before the next test counts rows.
        profileJpaRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

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
        assertThat(processedEventRepository.existsByEventId(eventId.toString())).isTrue();
    }

    /**
     * Regression guard for FIX A (TOCTOU race backstop).
     *
     * <p>Two threads invoke {@link AccountRegisteredConsumer#consume(String)}
     * DIRECTLY with the SAME eventId, released together via a start-gate so they
     * race as concurrently as possible. We call the injected bean (not {@code this})
     * so the {@code @Transactional} proxy opens an independent transaction per call —
     * which is exactly the concurrent flow that exercises the processed_events PK
     * backstop. Kafka's single-thread sequential delivery cannot reproduce this, so
     * the original "send twice" approach proved nothing.
     *
     * <p>Asserts:
     * <ol>
     *   <li>NEITHER task threw — with the {@code REQUIRES_NEW} {@code IdempotencyGuard}
     *       the race loser skips benignly and the consumer transaction is never poisoned,
     *       so no {@code UnexpectedRollbackException} escapes either thread.</li>
     *   <li>Exactly ONE {@code user_profiles} row exists for the account.</li>
     *   <li>Exactly ONE {@code processed_events} row exists for the eventId
     *       (counted, not {@code existsByEventId} which is true for 1 OR N).</li>
     * </ol>
     *
     * <p>Requires Docker (Testcontainers). If Docker is unavailable the test will
     * fail to start — do NOT weaken it to make it pass.
     */
    @Test
    void consumeAccountRegisteredEvent_concurrentSameEventId_oneProfileOneProcessedEvent() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String email = "concurrent-race@example.com";
        String message = buildContractMessage(eventId, accountId, tenantId, email, "Concurrent", "Race");

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger threwCount = new AtomicInteger(0);

        try {
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        consumer.consume(message);
                    } catch (Exception e) {
                        threwCount.incrementAndGet();
                    }
                    return null;
                }));
            }

            // Release both threads as simultaneously as the scheduler allows.
            startGate.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // NEITHER task threw: the REQUIRES_NEW guard prevents rollback poisoning, so the
        // race loser skips benignly instead of throwing UnexpectedRollbackException at commit.
        assertThat(threwCount.get())
                .as("No concurrent task may throw — REQUIRES_NEW guard prevents rollback poisoning")
                .isZero();

        // Exactly ONE profile row for the account.
        long profileCount = profileJpaRepository.findAll().stream()
                .filter(p -> accountId.equals(p.getId()))
                .count();
        assertThat(profileCount)
                .as("Expected exactly 1 UserProfile row for accountId %s", accountId)
                .isEqualTo(1);

        // Exactly ONE processed_events row for the eventId (count, not exists).
        long processedCount = processedEventRepository.findAll().stream()
                .filter(p -> eventId.toString().equals(p.getEventId()))
                .count();
        assertThat(processedCount)
                .as("Expected exactly 1 processed_events row for eventId %s", eventId)
                .isEqualTo(1);
    }

    /**
     * Regression guard for FIX A (genuine conflict must NOT be swallowed).
     *
     * <p>Two messages with DIFFERENT eventIds but the SAME tenant+email. The first
     * creates the profile; the second hits the {@code uix_user_profiles_tenant_email}
     * unique constraint. This is a real data conflict, NOT an idempotency duplicate,
     * so the second {@code consume()} MUST throw (propagating to the DLT) rather than
     * silently acking. Exactly one profile must remain.
     */
    @Test
    void consumeAccountRegisteredEvent_sameTenantEmailDifferentEventId_secondThrows() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String email = "genuine-conflict@example.com";

        UUID firstAccountId = UUID.randomUUID();
        UUID firstEventId = UUID.randomUUID();
        String firstMessage =
                buildContractMessage(firstEventId, firstAccountId, tenantId, email, "First", "User");

        UUID secondAccountId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        String secondMessage =
                buildContractMessage(secondEventId, secondAccountId, tenantId, email, "Second", "User");

        // First succeeds.
        consumer.consume(firstMessage);

        // Second is a genuine unique-constraint conflict — it MUST propagate.
        assertThatThrownBy(() -> consumer.consume(secondMessage))
                .as("A different eventId with the same tenant+email is a real conflict and must propagate")
                .isInstanceOf(RuntimeException.class);

        // Exactly one profile exists (the first); the conflicting insert rolled back.
        long profileCount = profileJpaRepository.findAll().stream()
                .filter(p -> tenantId.equals(p.getTenantId()) && email.equals(p.getEmail()))
                .count();
        assertThat(profileCount)
                .as("Only the first profile must exist for tenant %s / email %s", tenantId, email)
                .isEqualTo(1);
    }

    /**
     * Regression guard for poison-message handling.
     *
     * <p>A structurally invalid message (missing {@code payload}) is parsed up-front, BEFORE
     * the idempotency claim. Such a message will NEVER succeed on retry, so the consumer must
     * skip it WITHOUT rethrowing — it must not waste the error handler's retries or land in
     * the DLT. We invoke the bean directly and assert no exception escapes and nothing is
     * persisted.
     */
    @Test
    void consumeAccountRegisteredEvent_poisonMissingPayload_doesNotRethrow() throws Exception {
        UUID eventId = UUID.randomUUID();

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", "AccountRegistered");
        envelope.put("tenantId", UUID.randomUUID().toString());
        // Intentionally NO payload — structurally invalid (poison).
        String poison = objectMapper.writeValueAsString(envelope);

        // Must NOT throw — poison is logged and skipped, not rethrown.
        consumer.consume(poison);

        // Nothing claimed, nothing persisted.
        assertThat(processedEventRepository.existsByEventId(eventId.toString())).isFalse();
        assertThat(profileJpaRepository.findAll()).isEmpty();
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
