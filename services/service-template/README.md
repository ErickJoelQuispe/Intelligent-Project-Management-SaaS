# service-template — Canonical microservice template

This template is the **canonical reference** for a Java 21 / Spring Boot 3.5 microservice
in this monorepo. Every new service is bootstrapped by copying this module and renaming the
`Example` aggregate to the service's own aggregate. It demonstrates the **correct** outbox pattern
— the pattern that `auth-service` and `user-service` had wrong before being fixed.

> **Copy this, not auth-service. Copy this, not user-service.**

---

## Quick start

1. Copy `services/service-template/` to `services/your-service/`.
2. In your copy, do a global rename: `com.epm.template` → `com.epm.yourservice`, `service-template` → `your-service`.
3. Rename every file and class that contains `Example` or `example`.
4. Add your module to the parent `pom.xml` `<modules>` section.
5. Run `./mvnw verify -pl services/your-service -am --batch-mode` — it must be green before you add domain logic.

---

## Architecture

```
src/main/java/com/epm/template/
├── domain/                   ← pure Java, zero framework imports
│   ├── model/Example.java    ← aggregate root: holds domain events
│   ├── event/                ← plain records
│   └── port/
│       ├── in/               ← driving ports (use case interfaces)
│       └── out/              ← driven ports (repository, publisher, writer)
│
├── application/
│   └── usecase/              ← orchestrates domain; no Spring, no JPA, no Kafka
│
└── infrastructure/
    ├── adapter/
    │   ├── in/rest/          ← Spring MVC controllers + DTOs
    │   └── out/
    │       ├── messaging/    ← outbox publisher, relay executor/service, Kafka publisher
    │       └── persistence/  ← JPA entities, Spring Data repos, adapters, writer impl
    └── config/               ← @Configuration beans (Spring wiring only)
```

**Dependency rule**: `infrastructure` → `application` → `domain`. Never reversed.
`ArchitectureTest` enforces this on every build, including that `domain` and `application`
are free of Spring, JPA, Jackson, and Kafka imports.

---

## The Outbox pattern — read this carefully

### Why outbox?

If you call `repository.save(aggregate)` and then `eventPublisher.publish(event)` as two
separate operations, a crash between them loses the event permanently. The outbox pattern
solves this by writing the event as a database row **in the same transaction** as the aggregate.
A separate relay process reads pending rows and forwards them to Kafka. If the relay crashes,
it retries. Events are never lost.

### Step 1 — Atomic save+publish via `TransactionalExampleWriter`

The domain port `TransactionalExampleWriter` is the single save entry point for use cases:

```java
// domain/port/out/TransactionalExampleWriter.java
public interface TransactionalExampleWriter {
    Example saveAndPublish(Example example);
}
```

The infrastructure implementation (`TransactionalExampleWriterImpl`) is `@Transactional`:

```java
@Transactional
public Example saveAndPublish(Example example) {
    List<Object> events = example.pullDomainEvents(); // pull BEFORE save (see below)
    Example saved = repository.save(example);
    eventPublisher.publish(events);                  // inserts outbox rows — same tx
    return saved;
}
```

**Why pull events BEFORE save?** `ExampleRepositoryAdapter.save()` returns a fresh `Example`
via `Example.reconstitute()`, which has an empty domain-event list. If you called
`example.pullDomainEvents()` after the save, the events would already be gone.

**Use cases call `writer.saveAndPublish(aggregate)` — never `repository.save` + `publisher.publish` separately.**

### Step 2 — The relay and the self-invocation pitfall

The outbox relay has two classes on purpose:

| Class | Role |
|---|---|
| `OutboxRelayExecutor` | Does the actual work (`@Transactional relayBatch()`) |
| `OutboxRelayService` | Thin trigger only — injects executor, calls it cross-bean |

`OutboxRelayService` triggers the relay two ways:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOutboxEventSaved(OutboxEventSavedEvent event) {
    executor.relayBatch(); // cross-bean call → proxy honoured
}

@Scheduled(fixedDelay = 5000)
public void scheduledRelay() {
    executor.relayBatch(); // cross-bean call → proxy honoured
}
```

**Why NOT put `relayBatch()` in the same class?**

Spring's `@Transactional` works via a JDK/CGLIB proxy. When you call `this.relayBatch()`
from inside `OutboxRelayService`, the call bypasses the proxy — `@Transactional` is silently
ignored. The `FOR UPDATE SKIP LOCKED` database locks are released immediately after the query,
not at the end of the transaction. Concurrent relay threads can then pick up the same rows and
send duplicate Kafka messages.

By placing `relayBatch()` in a separate `@Component`, both triggers go through the Spring proxy.
`@Transactional` is honoured, locks are held for the full batch, and concurrent pods can never
grab the same row.

> **The rule**: if you need `@Transactional` + `@Scheduled`/`@TransactionalEventListener` in
> the same logical flow, put the transactional work in a separate bean and call it cross-bean.

### Step 3 — `FOR UPDATE SKIP LOCKED`

The relay queries in `OutboxEventJpaRepository` use native SQL with `FOR UPDATE SKIP LOCKED`:

```sql
SELECT * FROM outbox_events
WHERE published_at IS NULL AND failed_at IS NULL
ORDER BY created_at ASC LIMIT 10
FOR UPDATE SKIP LOCKED
```

`SKIP LOCKED` means: "lock the first 10 rows you can get without waiting; skip any row already
locked by another transaction." In a multi-pod deployment, each relay pod independently locks
a different batch — no row is processed twice in the same cycle.

There are two disjoint queries:

- **Pending batch**: `failed_at IS NULL` — fresh events never attempted
- **Retry batch**: `failed_at < :threshold` — events that failed more than 5 minutes ago

The two predicates are mutually exclusive (one requires `failed_at IS NULL`, the other requires
`failed_at IS NOT NULL`), so concatenating the two lists without `.distinct()` is correct by construction.

---

## Consumer idempotency — pattern (NOT implemented here)

> This template implements the **producer** outbox only. A service that also **consumes** events
> needs consumer-side idempotency. Here is the correct pattern to add when you need it.

### The processed_events table

```sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY  -- the eventId from the envelope
);
```

### The correct claim-first pattern

```java
@Transactional
public void handle(ExampleCreatedEnvelope envelope) {
    // Step 1: claim the event — INSERT will throw on duplicate PK
    try {
        processedEventsRepository.saveAndFlush(
            new ProcessedEventEntity(envelope.eventId()));
    } catch (DataIntegrityViolationException duplicate) {
        // Benign duplicate — already processed; ack and return
        log.info("Duplicate event {} — skipping", envelope.eventId());
        return;
    }
    // Step 2: do the business work inside the same transaction
    // Any genuine business constraint violation (not a PK conflict) propagates here.
    aggregateRepository.save(buildAggregate(envelope));
}
```

### Why NOT check-then-act

The naïve alternative is:

```java
// WRONG — TOCTOU race condition
if (processedEventsRepository.existsByEventId(envelope.eventId())) {
    return; // might race with concurrent consumer
}
processedEventsRepository.save(...); // two consumers can reach here simultaneously
```

Between the `existsByEventId` check and the `save`, a second concurrent consumer can pass
the check and process the same event twice. The INSERT-first approach is race-free: only one
transaction can hold the PK, and the loser gets a clean `DataIntegrityViolationException`
distinguishable from real business errors.

---

## Conventions

| Convention | Detail |
|---|---|
| Secrets | Never hardcoded; always `${ENV_VAR}` in `application.yml`. Add every new variable to `.env.example`. |
| `tenant_id` | Every business table must carry `tenant_id UUID NOT NULL`. |
| Schema management | `ddl-auto: validate` + Flyway migrations. Never `create` or `create-drop` in production. |
| Database in tests | Always real PostgreSQL via Testcontainers — **never H2 or any embedded DB**. Per `db.md` §12, H2 lacks JSONB, partial indexes, and `FOR UPDATE SKIP LOCKED`, so bugs that never surface in tests appear in production. The outbox relay depends on `SKIP LOCKED`; an H2 test profile would force mocking the relay and leave that SQL uncovered. |
| Test naming | `*IT.java` = Testcontainers integration tests (need Docker), run by Maven Failsafe under `mvn verify`. `*Test.java` = pure unit tests (no Docker), run by Maven Surefire under `mvn test`. The older `auth-service`/`user-service` named some Testcontainers tests `*Test` and are being migrated to `*IT`; this template reflects the intended convention. |
| Architecture rules | `ArchitectureTest` runs on every build. Domain and application layers must be free of Spring, JPA, Jackson, and Kafka. Add a rule for every new framework before introducing it anywhere in `domain` or `application`. |
| Event envelope | All outbox events use the envelope schema: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `aggregateId`, `aggregateType`, `tenantId`, `traceId`, `payload`. |
