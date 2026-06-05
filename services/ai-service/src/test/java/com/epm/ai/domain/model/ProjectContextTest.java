package com.epm.ai.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProjectContext value object.
 */
class ProjectContextTest {

    @Test
    void construction_withAllFields_storesValues() {
        List<String> members = List.of("Alice", "Bob", "Charlie");
        ProjectContext ctx = new ProjectContext("proj-1", "My Project", "A sample project", members);

        assertThat(ctx.projectId()).isEqualTo("proj-1");
        assertThat(ctx.name()).isEqualTo("My Project");
        assertThat(ctx.description()).isEqualTo("A sample project");
        assertThat(ctx.memberNames()).containsExactly("Alice", "Bob", "Charlie");
    }

    @Test
    void construction_withEmptyMembers_storesEmptyList() {
        ProjectContext ctx = new ProjectContext("proj-2", "Empty Project", "No members", List.of());

        assertThat(ctx.memberNames()).isEmpty();
        assertThat(ctx.projectId()).isEqualTo("proj-2");
    }

    @Test
    void equality_twoIdenticalContexts_areEqual() {
        List<String> members = List.of("Alice");
        ProjectContext c1 = new ProjectContext("p1", "name", "desc", members);
        ProjectContext c2 = new ProjectContext("p1", "name", "desc", members);

        assertThat(c1).isEqualTo(c2);
    }
}
