package com.epm.template.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ExampleJpaEntity}.
 *
 * <p>Package-private — only the adapter uses it directly.
 */
interface ExampleJpaRepository extends JpaRepository<ExampleJpaEntity, UUID> {}
