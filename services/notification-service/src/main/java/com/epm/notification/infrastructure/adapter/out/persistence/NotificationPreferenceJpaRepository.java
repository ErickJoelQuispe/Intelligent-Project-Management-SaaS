package com.epm.notification.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link NotificationPreferenceJpaEntity}.
 *
 * <p>Infrastructure layer — not accessible from domain or application.
 */
public interface NotificationPreferenceJpaRepository
        extends JpaRepository<NotificationPreferenceJpaEntity, UUID> {

    Optional<NotificationPreferenceJpaEntity> findByUserIdAndEventTypeAndChannel(
            UUID userId, NotificationType eventType, NotificationChannel channel);

    List<NotificationPreferenceJpaEntity> findAllByUserId(UUID userId);
}
