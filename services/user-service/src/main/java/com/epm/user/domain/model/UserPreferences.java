package com.epm.user.domain.model;

import java.time.ZoneId;
import java.util.Set;

import com.epm.user.domain.exception.InvalidPreferencesException;

/**
 * Value object representing a user's workspace preferences.
 *
 * <p>No Spring or JPA annotations — pure Java domain object.
 */
public record UserPreferences(
        String language,
        String timezone,
        String dateFormat,
        String startOfWeek) {

    private static final Set<String> VALID_LANGUAGES    = Set.of("en", "es", "pt");
    private static final Set<String> VALID_DATE_FORMATS = Set.of("ISO", "DD/MM/YYYY", "MM/DD/YYYY");
    private static final Set<String> VALID_WEEK_STARTS  = Set.of("MONDAY", "SUNDAY", "SATURDAY");

    /**
     * Returns the default preferences: English, UTC, ISO dates, Monday start.
     */
    public static UserPreferences defaults() {
        return new UserPreferences("en", "UTC", "ISO", "MONDAY");
    }

    /**
     * Validates all fields.
     *
     * @throws InvalidPreferencesException if any field contains an unsupported value
     */
    public void validate() {
        if (!VALID_LANGUAGES.contains(language)) {
            throw new InvalidPreferencesException("language", language);
        }
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new InvalidPreferencesException("timezone", timezone);
        }
        if (!VALID_DATE_FORMATS.contains(dateFormat)) {
            throw new InvalidPreferencesException("dateFormat", dateFormat);
        }
        if (!VALID_WEEK_STARTS.contains(startOfWeek)) {
            throw new InvalidPreferencesException("startOfWeek", startOfWeek);
        }
    }
}
