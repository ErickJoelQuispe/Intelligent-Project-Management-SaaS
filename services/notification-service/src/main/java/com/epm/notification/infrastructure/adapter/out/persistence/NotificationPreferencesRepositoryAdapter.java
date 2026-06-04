package com.epm.notification.infrastructure.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationPreference;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.out.NotificationPreferenceRepository;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing the {@link NotificationPreferenceRepository} port.
 *
 * <p>Upsert strategy: find existing row by (userId, eventType, channel);
 * if found, update its enabled flag and updatedAt; if not, insert new row.
 * This avoids unique constraint violations on concurrent upserts.
 */
@Component
public class NotificationPreferencesRepositoryAdapter implements NotificationPreferenceRepository {

    private final NotificationPreferenceJpaRepository jpaRepository;

    public NotificationPreferencesRepositoryAdapter(NotificationPreferenceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<NotificationPreference> findByUserIdAndEventTypeAndChannel(
            UUID userId, NotificationType eventType, NotificationChannel channel) {
        return jpaRepository
                .findByUserIdAndEventTypeAndChannel(userId, eventType, channel)
                .map(this::toDomain);
    }

    @Override
    public void upsert(NotificationPreference preference) {
        Optional<NotificationPreferenceJpaEntity> existing =
                jpaRepository.findByUserIdAndEventTypeAndChannel(
                        preference.getUserId(), preference.getEventType(), preference.getChannel());

        NotificationPreferenceJpaEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setEnabled(preference.isEnabled());
            entity.setUpdatedAt(LocalDateTime.now());
        } else {
            entity = toEntity(preference);
        }

        jpaRepository.save(entity);
    }

    @Override
    public List<NotificationPreference> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private NotificationPreferenceJpaEntity toEntity(NotificationPreference pref) {
        NotificationPreferenceJpaEntity entity = new NotificationPreferenceJpaEntity();
        entity.setId(pref.getId());
        entity.setTenantId(pref.getTenantId());
        entity.setUserId(pref.getUserId());
        entity.setEventType(pref.getEventType());
        entity.setChannel(pref.getChannel());
        entity.setEnabled(pref.isEnabled());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private NotificationPreference toDomain(NotificationPreferenceJpaEntity entity) {
        return new NotificationPreference(
                entity.getId(),
                entity.getUserId(),
                entity.getTenantId(),
                entity.getEventType(),
                entity.getChannel(),
                entity.isEnabled());
    }
}
