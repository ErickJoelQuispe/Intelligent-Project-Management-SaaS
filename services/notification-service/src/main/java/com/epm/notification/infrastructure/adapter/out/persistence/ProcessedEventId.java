package com.epm.notification.infrastructure.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary-key class for {@link ProcessedEventJpaEntity}, mapping the
 * {@code (event_id, topic)} key declared via {@code @IdClass}.
 *
 * <p>Field names ({@code eventId}, {@code topic}) and types must match the {@code @Id}-annotated
 * fields on the entity. Idempotency is scoped per topic so the same envelope eventId can be
 * recorded independently across different source topics.
 */
public class ProcessedEventId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String topic;

    public ProcessedEventId() {
    }

    public ProcessedEventId(String eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProcessedEventId that)) {
            return false;
        }
        return Objects.equals(eventId, that.eventId) && Objects.equals(topic, that.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, topic);
    }
}
