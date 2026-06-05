package com.epm.ai.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TaskPriority enum.
 */
class TaskPriorityTest {

    @Test
    void values_containsExpectedPriorities() {
        TaskPriority[] values = TaskPriority.values();

        assertThat(values).containsExactlyInAnyOrder(
                TaskPriority.HIGH,
                TaskPriority.MEDIUM,
                TaskPriority.LOW
        );
    }

    @Test
    void valueOf_highString_returnsHighPriority() {
        TaskPriority priority = TaskPriority.valueOf("HIGH");

        assertThat(priority).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    void valueOf_lowString_returnsLowPriority() {
        TaskPriority priority = TaskPriority.valueOf("LOW");

        assertThat(priority).isEqualTo(TaskPriority.LOW);
    }
}
