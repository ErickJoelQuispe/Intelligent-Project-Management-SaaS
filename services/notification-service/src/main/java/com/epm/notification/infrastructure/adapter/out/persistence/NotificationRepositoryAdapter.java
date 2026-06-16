package com.epm.notification.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.epm.notification.domain.model.Notification;
import com.epm.notification.domain.port.out.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter implementing the {@link NotificationRepository} port.
 *
 * <p>Translates between the domain model ({@link Notification}) and the
 * JPA entity ({@link NotificationJpaEntity}).
 */
@Component
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = toEntity(notification);
        jpaRepository.save(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Notification> findByTenantIdAndRecipientUserId(UUID tenantId, UUID recipientUserId) {
        return jpaRepository
                .findByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(tenantId, recipientUserId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Notification> findByTenantIdAndRecipientUserIdPaged(
            UUID tenantId, UUID recipientUserId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return jpaRepository
                .findByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(tenantId, recipientUserId, pageRequest)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Notification> findUnreadByTenantIdAndRecipientUserId(UUID tenantId, UUID recipientUserId) {
        return jpaRepository
                .findByTenantIdAndRecipientUserIdAndReadFalseOrderByCreatedAtDesc(tenantId, recipientUserId)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int countUnread(UUID tenantId, UUID recipientUserId) {
        return jpaRepository.countByTenantIdAndRecipientUserIdAndReadFalse(tenantId, recipientUserId);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID tenantId, UUID recipientUserId) {
        jpaRepository.markAllAsRead(tenantId, recipientUserId);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private NotificationJpaEntity toEntity(Notification notification) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.setId(notification.getId());
        entity.setTenantId(notification.getTenantId());
        entity.setRecipientUserId(notification.getRecipientUserId());
        entity.setType(notification.getType());
        entity.setReferenceId(notification.getReferenceId());
        entity.setMessage(notification.getMessage());
        entity.setRead(notification.isRead());
        entity.setCreatedAt(notification.getCreatedAt());
        return entity;
    }

    private Notification toDomain(NotificationJpaEntity entity) {
        return Notification.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getRecipientUserId(),
                entity.getType(),
                entity.getReferenceId(),
                entity.getMessage(),
                entity.isRead(),
                entity.getCreatedAt());
    }
}
