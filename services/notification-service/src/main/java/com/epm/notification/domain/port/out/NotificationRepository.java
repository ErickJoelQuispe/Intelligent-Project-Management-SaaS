package com.epm.notification.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.notification.domain.model.Notification;

/**
 * Output port for Notification persistence operations.
 *
 * <p>Implemented by the persistence adapter in the infrastructure layer.
 */
public interface NotificationRepository {

    /**
     * Persists a new or updated notification.
     *
     * @param notification the notification to save
     * @return the saved notification
     */
    Notification save(Notification notification);

    /**
     * Finds a notification by its ID.
     *
     * @param id the notification ID
     * @return an Optional containing the notification, or empty if not found
     */
    Optional<Notification> findById(UUID id);

    /**
     * Finds all notifications for a given user in a given tenant, newest first.
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the recipient user ID
     * @return list of notifications
     */
    List<Notification> findByTenantIdAndRecipientUserId(UUID tenantId, UUID recipientUserId);

    /**
     * Finds a page of notifications for a given user in a given tenant, newest first.
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the recipient user ID
     * @param page            zero-based page index
     * @param size            page size
     * @return list of notifications for the requested page
     */
    List<Notification> findByTenantIdAndRecipientUserIdPaged(UUID tenantId, UUID recipientUserId,
            int page, int size);

    /**
     * Finds unread notifications for a given user in a given tenant.
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the recipient user ID
     * @return list of unread notifications
     */
    List<Notification> findUnreadByTenantIdAndRecipientUserId(UUID tenantId, UUID recipientUserId);

    /**
     * Counts unread notifications for a given user in a given tenant.
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the recipient user ID
     * @return unread count
     */
    int countUnread(UUID tenantId, UUID recipientUserId);

    /**
     * Marks all unread notifications as read for a given user in a given tenant.
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the recipient user ID
     */
    void markAllAsRead(UUID tenantId, UUID recipientUserId);
}
