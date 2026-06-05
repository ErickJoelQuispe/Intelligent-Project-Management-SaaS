package com.epm.ai.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AiResponse value object.
 */
class AiResponseTest {

    @Test
    void construction_withAllFields_storesValues() {
        AiResponse response = new AiResponse("Task list generated", 120, 80, "deepseek-chat", false);

        assertThat(response.content()).isEqualTo("Task list generated");
        assertThat(response.inputTokens()).isEqualTo(120);
        assertThat(response.outputTokens()).isEqualTo(80);
        assertThat(response.model()).isEqualTo("deepseek-chat");
        assertThat(response.cached()).isFalse();
    }

    @Test
    void cached_whenTrue_reflectsValue() {
        AiResponse response = new AiResponse("Cached summary", 50, 200, "deepseek-chat", true);

        assertThat(response.cached()).isTrue();
        assertThat(response.content()).isEqualTo("Cached summary");
    }

    @Test
    void equality_twoIdenticalResponses_areEqual() {
        AiResponse r1 = new AiResponse("content", 10, 20, "model", false);
        AiResponse r2 = new AiResponse("content", 10, 20, "model", false);

        assertThat(r1).isEqualTo(r2);
    }
}
