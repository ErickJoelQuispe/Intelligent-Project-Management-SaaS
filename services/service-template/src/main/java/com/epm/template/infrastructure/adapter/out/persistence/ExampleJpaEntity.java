package com.epm.template.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code examples} table.
 *
 * <p>This is an infrastructure detail — the domain model ({@link com.epm.template.domain.model.Example})
 * knows nothing about this class. The adapter maps between them.
 */
@Entity
@Table(name = "examples")
class ExampleJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    protected ExampleJpaEntity() {}

    ExampleJpaEntity(UUID id, UUID tenantId, String name) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getName() {
        return name;
    }
}
