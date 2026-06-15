package com.epm.task.infrastructure.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * JPA entity mapping to the {@code processed_events} table.
 *
 * <p>Used for Kafka consumer idempotency — records events that have been processed
 * to prevent duplicate processing.
 *
 * <p><strong>Why this implements {@link Persistable} and forces {@code isNew() == true}.</strong>
 * The primary key ({@code event_id}) is an externally assigned {@code String}, not a generated
 * value. Spring Data's {@code SimpleJpaRepository.save()} decides between {@code persist()} and
 * {@code merge()} via {@code isNew()}; for an entity with a populated, assigned id it would
 * return {@code false} and call {@code merge()}. {@code merge()} performs a SELECT-then-
 * INSERT-or-UPDATE: on a <em>sequential</em> duplicate claim (the row is already committed and
 * visible), merge finds the existing row and issues an UPDATE — so NO
 * {@link org.springframework.dao.DataIntegrityViolationException} is thrown and the idempotency
 * guard would wrongly treat the duplicate as a fresh claim. By always reporting {@code isNew()},
 * {@code saveAndFlush()} forces an INSERT ({@code persist()}), which hits the
 * {@code pk_processed_events} primary key and raises the expected
 * {@code DataIntegrityViolationException} that the guard relies on for deduplication.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventJpaEntity implements Persistable<String> {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 255)
    private String eventId;

    @Column(nullable = false, updatable = false, length = 255)
    private String topic;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedEventJpaEntity() {
    }

    public ProcessedEventJpaEntity(String eventId, String topic, Instant processedAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.processedAt = processedAt;
    }

    // ── Persistable ──────────────────────────────────────────────────────────

    /**
     * The JPA identifier — the externally assigned eventId.
     */
    @Override
    public String getId() {
        return eventId;
    }

    /**
     * Always {@code true} so Spring Data issues an INSERT ({@code persist()}) rather than a
     * {@code merge()}. This is required for the idempotency claim to surface a duplicate-PK
     * {@link org.springframework.dao.DataIntegrityViolationException}. Claims are never updated,
     * so always treating the row as new is correct.
     */
    @Override
    @Transient
    public boolean isNew() {
        return true;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
