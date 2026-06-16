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

    /**
     * Returns a page of notifications for the given user in the given tenant, newest first.
     *
     * <p>The caller is responsible for clamping {@code page} and {@code size} to valid
     * ranges before invoking this method ({@code page >= 0}, {@code 1 <= size <= 100}).
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the recipient user ID
     * @param page            zero-based page index (must be >= 0)
     * @param size            page size (must be between 1 and 100)
     * @return list of notifications for the requested page
     */
    List<Notification> listForUserPaged(UUID tenantId, UUID recipientUserId, int page, int size);
}
