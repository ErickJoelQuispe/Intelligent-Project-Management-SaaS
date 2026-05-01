package com.epm.template.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Example} domain model.
 *
 * <p>No Spring context, no mocks framework — just plain Java.
 * Domain logic must be testable in microseconds.
 */
class ExampleTest {

    @Test
    void shouldCreateExampleWithValidData() {
        UUID id = UUID.randomUUID();
        Example example = new Example(id, "My example");

        assertThat(example.id()).isEqualTo(id);
        assertThat(example.name()).isEqualTo("My example");
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> new Example(UUID.randomUUID(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }

    @Test
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new Example(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }
}
