package com.epm.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.Team;
import com.epm.user.infrastructure.AbstractPostgresIT;
import com.epm.user.infrastructure.adapter.out.persistence.TeamJpaRepository;
import com.epm.user.infrastructure.adapter.out.persistence.TeamMembershipJpaRepository;
import com.epm.user.infrastructure.adapter.out.persistence.TeamPersistenceAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for {@link TeamPersistenceAdapter} — uses Testcontainers via AbstractPostgresIT.
 *
 * <p>Verifies CRUD operations and tenant isolation for teams.
 */
@DataJpaTest
@Import({TeamPersistenceAdapter.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class TeamPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    private TeamPersistenceAdapter adapter;

    @Autowired
    private TeamJpaRepository teamJpaRepository;

    @Autowired
    private TeamMembershipJpaRepository membershipJpaRepository;

    private UUID tenantId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        membershipJpaRepository.deleteAll();
        teamJpaRepository.deleteAll();
    }

    // ── save team → findById present ──────────────────────────────────────────

    @Test
    void save_and_findByIdAndTenantId_returnsTeam() {
        Team team = Team.create(tenantId, ownerId, "Backend Team", "Core backend squad");

        Team saved = adapter.save(team);

        Optional<Team> found = adapter.findByIdAndTenantId(saved.getId(), tenantId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
        assertThat(found.get().getName()).isEqualTo("Backend Team");
        assertThat(found.get().getMemberships()).hasSize(1); // owner is added automatically
    }

    // ── findAllByMemberUserId → returns only teams for that tenant ────────────

    @Test
    void findAllByMemberUserId_onlyReturnsTeamsForSameTenant() {
        // Tenant A — owner is a member
        Team teamA = Team.create(tenantId, ownerId, "Team A", "Tenant A team");
        adapter.save(teamA);

        // Tenant B — different tenant, same ownerId should NOT appear when querying tenantId
        UUID tenantB = UUID.randomUUID();
        Team teamB = Team.create(tenantB, ownerId, "Team B", "Tenant B team");
        adapter.save(teamB);

        List<Team> results = adapter.findAllByMemberUserId(ownerId, tenantId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Team A");
        assertThat(results.get(0).getTenantId()).isEqualTo(tenantId);
    }

    // ── findByIdAndTenantId with wrong tenantId → empty ───────────────────────

    @Test
    void findByIdAndTenantId_withWrongTenantId_returnsEmpty() {
        Team team = Team.create(tenantId, ownerId, "Frontend Team", null);
        Team saved = adapter.save(team);

        UUID differentTenantId = UUID.randomUUID();

        Optional<Team> found = adapter.findByIdAndTenantId(saved.getId(), differentTenantId);

        assertThat(found).isEmpty();
    }
}
