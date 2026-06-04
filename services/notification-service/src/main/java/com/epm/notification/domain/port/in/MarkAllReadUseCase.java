package com.epm.notification.domain.port.in;

import java.util.UUID;

/**
 * Input port: mark all notifications as read for a user in a tenant.
 */
public interface MarkAllReadUseCase {

    /**
     * Marks all notifications as read for the given user in the given tenant.
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the recipient user ID
     */
    void markAllAsRead(UUID tenantId, UUID recipientUserId);
}
