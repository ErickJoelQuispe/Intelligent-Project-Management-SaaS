package com.epm.auth.infrastructure.adapter.out.persistence;

import com.epm.auth.domain.model.SecurityEvent;
import com.epm.auth.domain.port.out.SecurityEventRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link SecurityEventRepository} port using JPA.
 *
 * <p>Security events are immutable — write-only, no retrieval needed from domain.
 */
@Component
public class SecurityEventPersistenceAdapter implements SecurityEventRepository {

    private final SecurityEventJpaRepository jpaRepository;

    public SecurityEventPersistenceAdapter(SecurityEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(SecurityEvent event) {
        SecurityEventJpaEntity entity = toEntity(event);
        jpaRepository.save(entity);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private SecurityEventJpaEntity toEntity(SecurityEvent event) {
        SecurityEventJpaEntity entity = new SecurityEventJpaEntity();
        entity.setId(event.id());
        entity.setTenantId(event.tenantId());
        entity.setAccountId(event.accountId());
        entity.setEventType(event.eventType());
        entity.setIpAddress(event.ipAddress());
        entity.setUserAgent(event.userAgent());
        entity.setOccurredAt(event.occurredAt());
        return entity;
    }
}
