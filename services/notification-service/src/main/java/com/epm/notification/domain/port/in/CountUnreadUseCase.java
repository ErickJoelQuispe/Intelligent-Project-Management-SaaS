package com.epm.notification.domain.port.in;

import java.util.UUID;

/**
 * Input port: count unread notifications for a user in a tenant.
 */
public interface CountUnreadUseCase {

    /**
     * Returns the count of unread notifications for the given user in the given tenant.
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the recipient user ID
     * @return number of unread notifications
     */
    int countUnread(UUID tenantId, UUID recipientUserId);
}
