package com.epm.template.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.epm.template.domain.event.ExampleCreatedEvent;

/**
 * Example aggregate root.
 *
 * <p>Domain objects are plain Java — no Spring, no JPA annotations here.
 * Infrastructure concerns (persistence, serialization) live in the adapters.
 *
 * <p>This aggregate accumulates domain events via the pull-events pattern:
 * internal operations record events into a private list; the transactional
 * writer calls {@link #pullDomainEvents()} before persisting, retrieves the
 * list, clears it, and passes the events to the outbox publisher — all within
 * a single transaction.
 */
public final class Example {

    private final UUID id;
    private final UUID tenantId;
    private final String name;
    private final List<Object> domainEvents = new ArrayList<>();

    private Example(UUID id, UUID tenantId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
    }

    /**
     * Factory: creates a new Example, generates its identity, validates invariants,
     * and records an {@link ExampleCreatedEvent} in the domain-event list.
     *
     * @param tenantId the tenant this example belongs to
     * @param name     a non-blank display name
     * @return a new, unsaved Example with one pending domain event
     */
    public static Example create(UUID tenantId, String name) {
        UUID id = UUID.randomUUID();
        Example example = new Example(id, tenantId, name);
        example.domainEvents.add(ExampleCreatedEvent.of(id, tenantId, name));
        return example;
    }

    /**
     * Factory: rehydrates an Example from persistent storage.
     *
     * <p>Does NOT record domain events — reconstitution is not a business
     * operation; it merely restores state from the database.
     *
     * @param id       the persisted identity
     * @param tenantId the tenant this example belongs to
     * @param name     the persisted name
     * @return a rehydrated Example with an empty domain-event list
     */
    public static Example reconstitute(UUID id, UUID tenantId, String name) {
        return new Example(id, tenantId, name);
    }

    /**
     * Returns all pending domain events and clears the internal list.
     *
     * <p>The transactional writer MUST call this BEFORE persisting the aggregate.
     * The persistence adapter re-creates the domain object from the JPA entity
     * (via {@link #reconstitute}), which means any in-memory events on the original
     * instance would be lost after {@code repository.save()}. Pulling first ensures
     * events are captured before the save occurs.
     *
     * @return a snapshot of pending domain events; the internal list is now empty
     */
    public List<Object> pullDomainEvents() {
        List<Object> events = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return events;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }
}
