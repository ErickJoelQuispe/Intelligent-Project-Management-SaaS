package com.epm.template.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.epm.template.domain.event.ExampleCreatedEvent;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Example} domain model.
 *
 * <p>No Spring context, no mocks framework — just plain Java.
 * Domain logic must be testable in microseconds.
 */
class ExampleTest {

    @Test
    void createRejectsBlankName() {
        UUID tenantId = UUID.randomUUID();
        assertThatThrownBy(() -> Example.create(tenantId, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }

    @Test
    void createRejectsNullName() {
        UUID tenantId = UUID.randomUUID();
        assertThatThrownBy(() -> Example.create(tenantId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }

    @Test
    void createRecordsExactlyOneExampleCreatedEvent() {
        UUID tenantId = UUID.randomUUID();
        Example example = Example.create(tenantId, "My example");

        List<Object> events = example.pullDomainEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ExampleCreatedEvent.class);

        ExampleCreatedEvent event = (ExampleCreatedEvent) events.get(0);
        assertThat(event.exampleId()).isEqualTo(example.id());
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.name()).isEqualTo("My example");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void createSetsCorrectFieldsOnAggregate() {
        UUID tenantId = UUID.randomUUID();
        Example example = Example.create(tenantId, "My example");

        assertThat(example.id()).isNotNull();
        assertThat(example.tenantId()).isEqualTo(tenantId);
        assertThat(example.name()).isEqualTo("My example");
    }

    @Test
    void pullDomainEventsClearsInternalList() {
        UUID tenantId = UUID.randomUUID();
        Example example = Example.create(tenantId, "Event test");

        // First pull: returns the recorded event
        List<Object> firstPull = example.pullDomainEvents();
        assertThat(firstPull).hasSize(1);

        // Second pull: list must be empty (cleared after first pull)
        List<Object> secondPull = example.pullDomainEvents();
        assertThat(secondPull).isEmpty();
    }

    @Test
    void reconstituteDoesNotRecordDomainEvents() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Example example = Example.reconstitute(id, tenantId, "Reconstituted");

        assertThat(example.id()).isEqualTo(id);
        assertThat(example.tenantId()).isEqualTo(tenantId);
        assertThat(example.name()).isEqualTo("Reconstituted");
        // Reconstitution is not a business operation — no events should be recorded
        assertThat(example.pullDomainEvents()).isEmpty();
    }

    @Test
    void reconstituteRejectsBlankName() {
        assertThatThrownBy(() -> Example.reconstitute(UUID.randomUUID(), UUID.randomUUID(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }
}
