package com.epm.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.epm.user.domain.exception.InvalidPreferencesException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UserPreferences} value object.
 */
class UserPreferencesTest {

    // ── defaults() ────────────────────────────────────────────────────────────

    @Test
    void defaultsReturnsEnLanguage() {
        UserPreferences prefs = UserPreferences.defaults();
        assertThat(prefs.language()).isEqualTo("en");
    }

    @Test
    void defaultsReturnsUtcTimezone() {
        UserPreferences prefs = UserPreferences.defaults();
        assertThat(prefs.timezone()).isEqualTo("UTC");
    }

    @Test
    void defaultsReturnsIsoDateFormat() {
        UserPreferences prefs = UserPreferences.defaults();
        assertThat(prefs.dateFormat()).isEqualTo("ISO");
    }

    @Test
    void defaultsReturnsMondayStartOfWeek() {
        UserPreferences prefs = UserPreferences.defaults();
        assertThat(prefs.startOfWeek()).isEqualTo("MONDAY");
    }

    // ── validate() happy path ─────────────────────────────────────────────────

    @Test
    void validateDoesNotThrowForValidPreferences() {
        UserPreferences prefs = new UserPreferences("es", "America/New_York", "DD/MM/YYYY", "SUNDAY");
        assertThatCode(prefs::validate).doesNotThrowAnyException();
    }

    @Test
    void validateDoesNotThrowForDefaults() {
        assertThatCode(() -> UserPreferences.defaults().validate()).doesNotThrowAnyException();
    }

    // ── validate() invalid language ───────────────────────────────────────────

    @Test
    void validateThrowsForInvalidLanguage() {
        UserPreferences prefs = new UserPreferences("fr", "UTC", "ISO", "MONDAY");
        assertThatThrownBy(prefs::validate)
                .isInstanceOf(InvalidPreferencesException.class)
                .hasMessageContaining("language");
    }

    // ── validate() invalid timezone ───────────────────────────────────────────

    @Test
    void validateThrowsForInvalidTimezone() {
        UserPreferences prefs = new UserPreferences("en", "Not/AZone", "ISO", "MONDAY");
        assertThatThrownBy(prefs::validate)
                .isInstanceOf(InvalidPreferencesException.class)
                .hasMessageContaining("timezone");
    }

    // ── validate() invalid dateFormat ─────────────────────────────────────────

    @Test
    void validateThrowsForInvalidDateFormat() {
        UserPreferences prefs = new UserPreferences("en", "UTC", "YYYY/MM/DD", "MONDAY");
        assertThatThrownBy(prefs::validate)
                .isInstanceOf(InvalidPreferencesException.class)
                .hasMessageContaining("dateFormat");
    }

    // ── validate() invalid startOfWeek ────────────────────────────────────────

    @Test
    void validateThrowsForInvalidStartOfWeek() {
        UserPreferences prefs = new UserPreferences("en", "UTC", "ISO", "WEDNESDAY");
        assertThatThrownBy(prefs::validate)
                .isInstanceOf(InvalidPreferencesException.class)
                .hasMessageContaining("startOfWeek");
    }

    // ── TRIANGULATE: multiple valid values ────────────────────────────────────

    @Test
    void validateAcceptsPortugueseLanguage() {
        UserPreferences prefs = new UserPreferences("pt", "UTC", "MM/DD/YYYY", "SATURDAY");
        assertThatCode(prefs::validate).doesNotThrowAnyException();
    }
}
