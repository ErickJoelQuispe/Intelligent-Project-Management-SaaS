package com.epm.template.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ExampleJpaEntity}.
 *
 * <p>Public so that integration tests ({@code *IT}) can autowire it directly
 * to verify row-level state after transactional operations.
 */
public interface ExampleJpaRepository extends JpaRepository<ExampleJpaEntity, UUID> {}
