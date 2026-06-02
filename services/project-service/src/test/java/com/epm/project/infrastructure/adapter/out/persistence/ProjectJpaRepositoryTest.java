package com.epm.project.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

/**
 * @DataJpaTest for {@link ProjectJpaRepository}.
 *
 * <p>Uses the real {@code project_test} PostgreSQL database — no H2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class ProjectJpaRepositoryTest {

    @Autowired
    private ProjectJpaRepository projectJpaRepository;

    @Autowired
    private ProjectMemberJpaRepository memberJpaRepository;

    @Autowired
    private ProjectTeamJpaRepository teamJpaRepository;

    // ── findByIdAndTenantIdAndDeletedAtIsNull ────────────────────────────────

    @Test
    void findByIdAndTenantId_returnsProject_whenPresent() {
        UUID tenantId = UUID.randomUUID();
        ProjectJpaEntity project = buildProject(tenantId);
        projectJpaRepository.save(project);

        Optional<ProjectJpaEntity> found =
                projectJpaRepository.findByIdAndTenantIdAndDeletedAtIsNull(project.getId(), tenantId);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Project");
    }

    @Test
    void findByIdAndTenantId_returnsEmpty_whenMissing() {
        Optional<ProjectJpaEntity> found =
                projectJpaRepository.findByIdAndTenantIdAndDeletedAtIsNull(UUID.randomUUID(), UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdAndTenantId_returnsEmpty_whenDeletedAtIsSet() {
        UUID tenantId = UUID.randomUUID();
        ProjectJpaEntity project = buildProject(tenantId);
        project.setDeletedAt(Instant.now());
        projectJpaRepository.save(project);

        Optional<ProjectJpaEntity> found =
                projectJpaRepository.findByIdAndTenantIdAndDeletedAtIsNull(project.getId(), tenantId);

        assertThat(found).isEmpty();
    }

    // ── findAllProjectsByMemberProfileId ─────────────────────────────────────

    @Test
    void findAllProjectsByMemberProfileId_returnsProjects_forActiveMember() {
        UUID tenantId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        ProjectJpaEntity project = buildProject(tenantId);
        projectJpaRepository.save(project);

        ProjectMemberJpaEntity member = buildMember(project.getId(), profileId, tenantId);
        memberJpaRepository.save(member);

        List<ProjectJpaEntity> results =
                projectJpaRepository.findAllProjectsByMemberProfileId(profileId, tenantId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(project.getId());
    }

    @Test
    void findAllProjectsByMemberProfileId_excludesRemovedMemberships() {
        UUID tenantId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        ProjectJpaEntity project = buildProject(tenantId);
        projectJpaRepository.save(project);

        ProjectMemberJpaEntity member = buildMember(project.getId(), profileId, tenantId);
        member.setRemovedAt(Instant.now());
        memberJpaRepository.save(member);

        List<ProjectJpaEntity> results =
                projectJpaRepository.findAllProjectsByMemberProfileId(profileId, tenantId);

        assertThat(results).isEmpty();
    }

    // ── unique member constraint ──────────────────────────────────────────────

    @Test
    void uniqueMemberConstraint_throwsOnDuplicateActiveEntry() {
        UUID tenantId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        ProjectJpaEntity project = buildProject(tenantId);
        projectJpaRepository.save(project);

        ProjectMemberJpaEntity member1 = buildMember(project.getId(), profileId, tenantId);
        memberJpaRepository.save(member1);
        memberJpaRepository.flush();

        ProjectMemberJpaEntity member2 = buildMember(project.getId(), profileId, tenantId);

        assertThatThrownBy(() -> {
            memberJpaRepository.save(member2);
            memberJpaRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProjectJpaEntity buildProject(UUID tenantId) {
        ProjectJpaEntity e = new ProjectJpaEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setOwnerId(UUID.randomUUID());
        e.setName("Test Project");
        e.setStatus("ACTIVE");
        e.setVisibility("PRIVATE");
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        e.setCreatedBy("system");
        e.setUpdatedBy("system");
        return e;
    }

    private ProjectMemberJpaEntity buildMember(UUID projectId, UUID profileId, UUID tenantId) {
        ProjectMemberJpaEntity e = new ProjectMemberJpaEntity();
        e.setId(UUID.randomUUID());
        e.setProjectId(projectId);
        e.setProfileId(profileId);
        e.setRole("OWNER");
        e.setJoinedAt(Instant.now());
        e.setTenantId(tenantId);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        e.setCreatedBy("system");
        e.setUpdatedBy("system");
        return e;
    }
}
