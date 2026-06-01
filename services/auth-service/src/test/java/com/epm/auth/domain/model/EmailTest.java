package com.epm.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Email} value object.
 *
 * <p>Tests run RED first — Email class does not exist yet at the time of writing.
 */
class EmailTest {

    @Test
    void validEmailIsAccepted() {
        Email email = new Email("alice@example.com");
        assertThat(email.value()).isEqualTo("alice@example.com");
    }

    @Test
    void invalidEmailThrowsException() {
        assertThatThrownBy(() -> new Email("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid email");
    }

    @Test
    void nullEmailThrowsException() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emailIsNormalizedToLowercase() {
        Email email = new Email("ALICE@EXAMPLE.COM");
        assertThat(email.value()).isEqualTo("alice@example.com");
    }

    @Test
    void emailWithMixedCaseIsNormalized() {
        Email email = new Email("Bob.Smith@Example.Org");
        assertThat(email.value()).isEqualTo("bob.smith@example.org");
    }
}
