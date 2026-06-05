package com.epm.ai.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AiRequest value object.
 */
class AiRequestTest {

    @Test
    void construction_withAllFields_storesValues() {
        AiRequest request = new AiRequest("Build login page tasks", "proj-1", "user-1", "tenant-1");

        assertThat(request.prompt()).isEqualTo("Build login page tasks");
        assertThat(request.projectId()).isEqualTo("proj-1");
        assertThat(request.userId()).isEqualTo("user-1");
        assertThat(request.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void equality_twoIdenticalRequests_areEqual() {
        AiRequest r1 = new AiRequest("summary", "p1", "u1", "t1");
        AiRequest r2 = new AiRequest("summary", "p1", "u1", "t1");

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void equality_differentPrompt_notEqual() {
        AiRequest r1 = new AiRequest("prompt A", "p1", "u1", "t1");
        AiRequest r2 = new AiRequest("prompt B", "p1", "u1", "t1");

        assertThat(r1).isNotEqualTo(r2);
    }
}
