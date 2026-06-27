package com.epm.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.event.TeamCreated;
import com.epm.user.domain.event.TeamDeleted;
import com.epm.user.domain.event.TeamMemberJoined;
import com.epm.user.domain.event.TeamMemberLeft;
import com.epm.user.domain.event.TeamUpdated;
import com.epm.user.domain.exception.DuplicateMemberException;
import com.epm.user.domain.exception.LastOwnerException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Team} aggregate root.
 *
 * <p>Tests run RED first — Team class does not exist yet.
 */
class TeamTest {

    @Test
    void createSetsIdTenantIdOwnerIdAndName() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha Team", "description");
        assertThat(team.getId()).isNotNull();
        assertThat(team.getTenantId()).isEqualTo(tenantId);
        assertThat(team.getOwnerId()).isEqualTo(ownerId);
        assertThat(team.getName()).isEqualTo("Alpha Team");
    }

    @Test
    void createAddsCreatorAsOwnerMembership() {
        UUID ownerId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Alpha Team", null);
        List<TeamMembership> memberships = team.getMemberships();
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).getUserId()).isEqualTo(ownerId);
        assertThat(memberships.get(0).getRole()).isEqualTo(TeamRole.OWNER);
    }

    @Test
    void createRecordsTeamCreatedDomainEvent() {
        Team team = Team.create(UUID.randomUUID(), UUID.randomUUID(), "Alpha Team", null);
        List<Object> events = team.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TeamCreated.class);
    }

    @Test
    void addMemberAddsActiveMembershipWithGivenRole() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Alpha Team", null);
        team.pullDomainEvents(); // clear creation events
        team.addMember(memberId, TeamRole.MEMBER);
        List<TeamMembership> active = team.getMemberships().stream()
                .filter(m -> m.getUserId().equals(memberId) && m.isActive())
                .toList();
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getRole()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void addMemberThrowsDuplicateMemberExceptionIfAlreadyActive() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Alpha Team", null);
        team.addMember(memberId, TeamRole.MEMBER);
        // Add the same member again — should throw
        assertThatThrownBy(() -> team.addMember(memberId, TeamRole.VIEWER))
                .isInstanceOf(DuplicateMemberException.class);
    }

    @Test
    void removeMemberSoftRemovesMembership() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Alpha Team", null);
        team.addMember(memberId, TeamRole.MEMBER);
        team.removeMember(memberId);
        boolean isStillActive = team.getMemberships().stream()
                .filter(m -> m.getUserId().equals(memberId))
                .anyMatch(TeamMembership::isActive);
        assertThat(isStillActive).isFalse();
    }

    @Test
    void removeMemberThrowsLastOwnerExceptionWhenRemovingLastOwner() {
        UUID ownerId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Alpha Team", null);
        // Only one owner — removing them should throw
        assertThatThrownBy(() -> team.removeMember(ownerId))
                .isInstanceOf(LastOwnerException.class);
    }

    @Test
    void removeMemberRecordsTeamMemberLeftEvent() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Alpha Team", null);
        team.addMember(memberId, TeamRole.MEMBER);
        team.pullDomainEvents(); // clear creation/join events
        team.removeMember(memberId);
        List<Object> events = team.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TeamMemberLeft.class);
    }

    @Test
    void delete_publishes_TeamDeleted_event() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Alpha Team", null);
        team.pullDomainEvents(); // clear creation events

        team.delete();

        List<Object> events = team.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TeamDeleted.class);
        TeamDeleted deleted = (TeamDeleted) events.get(0);
        assertThat(deleted.teamId()).isEqualTo(team.getId());
        assertThat(deleted.tenantId()).isEqualTo(tenantId);
        assertThat(deleted.eventId()).isNotNull();
        assertThat(deleted.occurredAt()).isNotNull();
    }

    @Test
    void delete_sets_deletedAt() {
        UUID ownerId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Alpha Team", null);

        assertThat(team.getDeletedAt()).isNull();
        team.delete();
        assertThat(team.getDeletedAt()).isNotNull();
    }

    @Test
    void addMember_emitsTeamMemberJoined_withTeamName() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Engineering", null);
        team.pullDomainEvents(); // clear creation events

        team.addMember(memberId, TeamRole.MEMBER);

        List<Object> events = team.pullDomainEvents();
        assertThat(events).hasSize(1);
        TeamMemberJoined event = (TeamMemberJoined) events.get(0);
        assertThat(event.teamName()).isEqualTo("Engineering");
    }

    @Test
    void removeMember_emitsTeamMemberLeft_withTeamName() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Design Guild", null);
        team.addMember(memberId, TeamRole.MEMBER);
        team.pullDomainEvents(); // clear prior events

        team.removeMember(memberId);

        List<Object> events = team.pullDomainEvents();
        assertThat(events).hasSize(1);
        TeamMemberLeft event = (TeamMemberLeft) events.get(0);
        assertThat(event.teamName()).isEqualTo("Design Guild");
    }

    // ── update() tests (Phase 1 — TDD RED) ───────────────────────────────────

    @Test
    void update_withBothFields_mutatesNameAndDescription() {
        UUID ownerId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Old Name", "Old Desc");
        team.pullDomainEvents();

        team.update("New Name", "New Desc");

        assertThat(team.getName()).isEqualTo("New Name");
        assertThat(team.getDescription()).isEqualTo("New Desc");
    }

    @Test
    void update_withNewNameOnly_keepsPreviousDescription() {
        UUID ownerId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Old Name", "Kept Desc");
        team.pullDomainEvents();

        team.update("New Name", null);

        assertThat(team.getName()).isEqualTo("New Name");
        assertThat(team.getDescription()).isEqualTo("Kept Desc");
    }

    @Test
    void update_withNewDescOnly_keepsPreviousName() {
        UUID ownerId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Kept Name", "Old Desc");
        team.pullDomainEvents();

        team.update(null, "New Desc");

        assertThat(team.getName()).isEqualTo("Kept Name");
        assertThat(team.getDescription()).isEqualTo("New Desc");
    }

    @Test
    void update_withBlankName_throwsIllegalArgumentException() {
        UUID ownerId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Old Name", null);
        team.pullDomainEvents();

        assertThatThrownBy(() -> team.update("  ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_withBothNull_throwsIllegalArgumentException() {
        UUID ownerId = UUID.randomUUID();
        Team team = Team.create(UUID.randomUUID(), ownerId, "Old Name", null);
        team.pullDomainEvents();

        assertThatThrownBy(() -> team.update(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_emitsTeamUpdatedEvent() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team = Team.create(tenantId, ownerId, "Old Name", null);
        team.pullDomainEvents();

        team.update("New Name", "New Desc");

        List<Object> events = team.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TeamUpdated.class);
        TeamUpdated event = (TeamUpdated) events.get(0);
        assertThat(event.teamId()).isEqualTo(team.getId());
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.name()).isEqualTo("New Name");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
    }
}
