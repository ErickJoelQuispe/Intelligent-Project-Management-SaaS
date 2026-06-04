package com.epm.notification.domain.port.in;

import java.util.UUID;

import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationType;

/**
 * In port for updating a single notification preference for a user.
 */
public interface UpdatePreferenceUseCase {

    void updatePreference(UUID userId, UUID tenantId,
            NotificationType eventType, NotificationChannel channel, boolean enabled);
}
