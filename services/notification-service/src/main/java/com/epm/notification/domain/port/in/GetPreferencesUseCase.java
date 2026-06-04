package com.epm.notification.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.epm.notification.domain.model.NotificationPreference;

/**
 * In port for retrieving all notification preferences for a user.
 *
 * <p>Returns the full list of preferences including defaults ({@code enabled=true})
 * for event types not yet saved in the database.
 */
public interface GetPreferencesUseCase {

    List<NotificationPreference> getPreferences(UUID userId, UUID tenantId);
}
