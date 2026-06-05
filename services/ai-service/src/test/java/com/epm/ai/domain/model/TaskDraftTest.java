package com.epm.ai.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TaskDraft value object.
 */
class TaskDraftTest {

    @Test
    void construction_withAllFields_storesValues() {
        TaskDraft draft = new TaskDraft("Set up CI/CD", "Configure GitHub Actions pipeline", TaskPriority.HIGH);

        assertThat(draft.title()).isEqualTo("Set up CI/CD");
        assertThat(draft.description()).isEqualTo("Configure GitHub Actions pipeline");
        assertThat(draft.priority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    void construction_withMediumPriority_reflectsValue() {
        TaskDraft draft = new TaskDraft("Write tests", "Add unit tests for domain layer", TaskPriority.MEDIUM);

        assertThat(draft.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(draft.title()).isEqualTo("Write tests");
    }

    @Test
    void equality_twoIdenticalDrafts_areEqual() {
        TaskDraft d1 = new TaskDraft("title", "desc", TaskPriority.LOW);
        TaskDraft d2 = new TaskDraft("title", "desc", TaskPriority.LOW);

        assertThat(d1).isEqualTo(d2);
        assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
    }
}
