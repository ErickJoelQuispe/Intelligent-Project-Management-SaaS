package com.epm.notification.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.epm.notification.domain.model.Notification;

/**
 * Input port: list notifications for a user in a tenant.
 */
public interface ListNotificationsUseCase {

    /**
     * Returns all notifications for the given user in the given tenant, newest first.
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the recipient user ID
     * @return list of notifications
     */
    List<Notification> listForUser(UUID tenantId, UUID recipientUserId);
}
