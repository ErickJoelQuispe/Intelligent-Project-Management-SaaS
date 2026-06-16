package com.epm.notification.infrastructure.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA entity mapping to the {@code processed_events} table.
 *
 * <p>Used for Kafka consumer idempotency — records events that have been processed
 * to prevent duplicate processing.
 *
 * <p><strong>Composite primary key {@code (event_id, topic)}.</strong> Idempotency is scoped per
 * topic: the same envelope eventId can legitimately arrive on two DIFFERENT source topics
 * (project.events, task.events, user.events) as distinct domain events. A single-column PK on
 * {@code event_id} would drop the second topic's event. The composite key is mapped with
 * {@link IdClass} ({@link ProcessedEventId}) — the minimal mapping that keeps both columns as
 * plain entity fields while declaring the two-column key.
 *
 * <p><strong>No {@code Persistable} needed.</strong> Claims are made via an atomic native
 * {@code INSERT ... ON CONFLICT DO NOTHING} ({@code ProcessedEventJpaRepository.claimEvent}),
 * which bypasses {@code SimpleJpaRepository.save()} entirely. This entity is therefore only ever
 * read ({@code findAll}) or bulk-deleted ({@code deleteAll}) — operations that do not depend on
 * {@code isNew()} — so the previous {@code Persistable<String>} machinery (which existed solely to
 * force {@code persist()} over {@code merge()} for duplicate-PK detection) is no longer required.
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 255)
    private String eventId;

    @Id
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

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
