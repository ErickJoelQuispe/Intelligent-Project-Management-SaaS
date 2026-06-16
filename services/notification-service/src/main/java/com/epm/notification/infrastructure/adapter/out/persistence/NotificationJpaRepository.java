package com.epm.notification.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link NotificationJpaEntity}.
 */
public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    List<NotificationJpaEntity> findByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(
            UUID tenantId, UUID recipientUserId);

    /**
     * Returns a page of notifications for the given user in the given tenant, newest first.
     *
     * @param tenantId        the tenant ID
     * @param recipientUserId the recipient user ID
     * @param pageable        page request (offset + limit)
     * @return paged list of notifications
     */
    List<NotificationJpaEntity> findByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(
            UUID tenantId, UUID recipientUserId, Pageable pageable);

    List<NotificationJpaEntity> findByTenantIdAndRecipientUserIdAndReadFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID recipientUserId);

    int countByTenantIdAndRecipientUserIdAndReadFalse(UUID tenantId, UUID recipientUserId);

    @Modifying
    @Query("UPDATE NotificationJpaEntity n SET n.read = true " +
           "WHERE n.tenantId = :tenantId AND n.recipientUserId = :recipientUserId AND n.read = false")
    void markAllAsRead(@Param("tenantId") UUID tenantId, @Param("recipientUserId") UUID recipientUserId);
}
