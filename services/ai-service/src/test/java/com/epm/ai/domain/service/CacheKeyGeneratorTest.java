package com.epm.ai.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CacheKeyGenerator (pure function — no mocks needed).
 */
class CacheKeyGeneratorTest {

    // --- Deterministic output ---

    @Test
    void generate_sameParts_producesIdenticalKey() {
        String key1 = CacheKeyGenerator.generate("proj-1", "Build login page tasks");
        String key2 = CacheKeyGenerator.generate("proj-1", "Build login page tasks");

        assertThat(key1).isEqualTo(key2);
        assertThat(key1).hasSize(64); // SHA-256 hex is always 64 chars
    }

    // --- Whitespace normalization ---

    @Test
    void generate_extraWhitespaceTrimmed_sameKeyAsTrimmed() {
        String keyTrimmed = CacheKeyGenerator.generate("proj-1", "Build login");
        String keyWithSpaces = CacheKeyGenerator.generate("  proj-1  ", "  Build   login  ");

        assertThat(keyTrimmed).isEqualTo(keyWithSpaces);
    }

    @Test
    void generate_internalWhitespaceCollapsed_sameKeyAsCollapsed() {
        String keyNormal = CacheKeyGenerator.generate("This is a prompt");
        String keyMultiSpace = CacheKeyGenerator.generate("This  is   a    prompt");

        assertThat(keyNormal).isEqualTo(keyMultiSpace);
    }

    // --- Different inputs produce different keys ---

    @Test
    void generate_differentParts_producesDifferentKeys() {
        String keyA = CacheKeyGenerator.generate("proj-1", "Generate tasks");
        String keyB = CacheKeyGenerator.generate("proj-2", "Generate tasks");

        assertThat(keyA).isNotEqualTo(keyB);
    }

    @Test
    void generate_differentPrompt_producesDifferentKey() {
        String keyA = CacheKeyGenerator.generate("proj-1", "Generate tasks");
        String keyB = CacheKeyGenerator.generate("proj-1", "Summarize project");

        assertThat(keyA).isNotEqualTo(keyB);
    }

    // --- Multiple parts ---

    @Test
    void generate_multiplePartsJoined_producesConsistentKey() {
        String key = CacheKeyGenerator.generate("project-summary", "proj-1", "2026-06-05");

        assertThat(key).isNotNull();
        assertThat(key).hasSize(64);
    }
}
