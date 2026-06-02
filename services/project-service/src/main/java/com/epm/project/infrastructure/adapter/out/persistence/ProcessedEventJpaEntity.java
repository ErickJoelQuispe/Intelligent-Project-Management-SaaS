package com.epm.project.infrastructure.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity mapping to the {@code processed_events} table.
 *
 * <p>Used for Kafka consumer idempotency — records events that have been processed
 * to prevent duplicate processing.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventJpaEntity {

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

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
