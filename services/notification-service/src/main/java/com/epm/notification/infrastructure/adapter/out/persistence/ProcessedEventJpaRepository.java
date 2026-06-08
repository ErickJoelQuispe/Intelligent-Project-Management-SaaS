package com.epm.notification.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ProcessedEventJpaEntity}.
 */
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, String> {

    boolean existsByEventId(String eventId);
}
