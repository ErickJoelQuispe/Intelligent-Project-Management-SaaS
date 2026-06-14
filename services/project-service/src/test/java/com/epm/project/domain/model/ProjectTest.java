package com.epm.project.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import java.time.Instant;
import java.util.Set;

import com.epm.project.domain.event.ProjectArchived;
import com.epm.project.domain.event.ProjectCreated;
import com.epm.project.domain.event.ProjectUpdated;
import com.epm.project.domain.event.TeamAssignedToProject;
import com.epm.project.domain.exception.DuplicateProjectMemberException;
import com.epm.project.domain.exception.DuplicateTeamAssignmentException;
import com.epm.project.domain.exception.UnauthorizedProjectAccessException;
import com.epm.project.domain.port.in.command.CreateProjectCommand;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Project} aggregate root.
 *
 * <p>RED phase — domain model does not exist yet.
 */
class ProjectTest {

    // ── create() tests ─────────────────────────────────────────────────────

    @Test
    void create_sets_id_tenantId_ownerId_name_and_status_active() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        CreateProjectCommand cmd = new CreateProjectCommand(
                "Alpha Project", "A description", ProjectVisibility.PRIVATE, ownerId, tenantId);

        Project project = Project.create(cmd);

        assertThat(project.getId()).isNotNull();
        assertThat(project.getTenantId()).isEqualTo(tenantId);
        assertThat(project.getOwnerProfileId()).isEqualTo(ownerId);
        assertThat(project.getName()).isEqualTo("Alpha Project");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    void create_adds_owner_as_project_member_with_OWNER_role() {
        UUID ownerId = UUID.randomUUID();
        CreateProjectCommand cmd = new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID());

        Project project = Project.create(cmd);

        List<ProjectMember> members = project.getMembers();
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getProfileId()).isEqualTo(ownerId);
        assertThat(members.get(0).getRole()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void create_emits_ProjectCreated_event() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        CreateProjectCommand cmd = new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, tenantId);

        Project project = Project.create(cmd);
        List<Object> events = project.pullDomainEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ProjectCreated.class);
        ProjectCreated evt = (ProjectCreated) events.get(0);
        assertThat(evt.projectId()).isEqualTo(project.getId());
        assertThat(evt.tenantId()).isEqualTo(tenantId);
        assertThat(evt.ownerId()).isEqualTo(ownerId);
    }

    @Test
    void create_blank_name_throws_IllegalArgumentException() {
        UUID ownerId = UUID.randomUUID();
        CreateProjectCommand cmd = new CreateProjectCommand(
                "  ", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID());

        assertThatThrownBy(() -> Project.create(cmd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_name_over_100_chars_throws_IllegalArgumentException() {
        UUID ownerId = UUID.randomUUID();
        String longName = "A".repeat(101);
        CreateProjectCommand cmd = new CreateProjectCommand(
                longName, null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID());

        assertThatThrownBy(() -> Project.create(cmd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pullDomainEvents_clears_internal_list() {
        CreateProjectCommand cmd = new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE,
                UUID.randomUUID(), UUID.randomUUID());
        Project project = Project.create(cmd);

        project.pullDomainEvents(); // first pull
        List<Object> second = project.pullDomainEvents();

        assertThat(second).isEmpty();
    }

    // ── update() tests ─────────────────────────────────────────────────────

    @Test
    void update_changes_name_description_visibility_and_emits_ProjectUpdated() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Old Name", "Old desc", ProjectVisibility.PRIVATE, ownerId, tenantId));
        project.pullDomainEvents();

        project.update("New Name", "New desc", ProjectVisibility.PUBLIC, ownerId);

        assertThat(project.getName()).isEqualTo("New Name");
        assertThat(project.getDescription()).isEqualTo("New desc");
        assertThat(project.getVisibility()).isEqualTo(ProjectVisibility.PUBLIC);
        List<Object> events = project.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ProjectUpdated.class);
    }

    @Test
    void update_by_non_owner_non_manager_throws_UnauthorizedProjectAccessException() {
        UUID ownerId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));

        assertThatThrownBy(() -> project.update("X", "Y", ProjectVisibility.PRIVATE, stranger))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    // ── archive() tests ────────────────────────────────────────────────────

    @Test
    void archive_by_owner_sets_status_ARCHIVED_and_emits_ProjectArchived() {
        UUID ownerId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));
        project.pullDomainEvents();

        project.archive(ownerId);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        List<Object> events = project.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ProjectArchived.class);
    }

    @Test
    void archive_by_non_owner_throws_UnauthorizedProjectAccessException() {
        UUID ownerId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));

        assertThatThrownBy(() -> project.archive(stranger))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    @Test
    void archive_already_archived_project_is_noop_no_duplicate_event() {
        UUID ownerId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));
        project.pullDomainEvents(); // clear create event

        project.archive(ownerId); // first archive — emits event
        Instant firstUpdatedAt = project.getUpdatedAt();
        project.pullDomainEvents(); // consume the event

        project.archive(ownerId); // second archive — must be idempotent (no-op)

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        assertThat(project.pullDomainEvents()).as("re-archive must emit no new event").isEmpty();
        assertThat(project.getUpdatedAt())
                .as("updatedAt must not change on a no-op re-archive")
                .isEqualTo(firstUpdatedAt);
    }

    @Test
    void archive_non_owner_on_already_archived_still_throws_UnauthorizedProjectAccessException() {
        UUID ownerId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));
        project.archive(ownerId); // archive it first
        project.pullDomainEvents();

        // A non-owner calling archive on an already-archived project must still be rejected (authz first)
        assertThatThrownBy(() -> project.archive(stranger))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    // ── assignTeam() tests ─────────────────────────────────────────────────

    @Test
    void assignTeam_adds_team_and_emits_TeamAssignedToProject() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, tenantId));
        project.pullDomainEvents();

        project.assignTeam(teamId, ownerId);

        List<ProjectTeam> teams = project.getTeams();
        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).getTeamId()).isEqualTo(teamId);
        assertThat(teams.get(0).isActive()).isTrue();
        List<Object> events = project.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TeamAssignedToProject.class);
    }

    @Test
    void assignTeam_duplicate_active_team_throws_DuplicateTeamAssignmentException() {
        UUID ownerId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));
        project.assignTeam(teamId, ownerId);

        assertThatThrownBy(() -> project.assignTeam(teamId, ownerId))
                .isInstanceOf(DuplicateTeamAssignmentException.class);
    }

    @Test
    void assignTeam_by_non_owner_non_manager_throws_UnauthorizedProjectAccessException() {
        UUID ownerId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));

        assertThatThrownBy(() -> project.assignTeam(teamId, stranger))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    // ── addMember() / removeMember() / isMember() / hasRole() tests ────────

    @Test
    void addMember_throws_DuplicateProjectMemberException_if_already_active() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));
        project.addMember(memberId, ProjectRole.CONTRIBUTOR, ownerId);

        assertThatThrownBy(() -> project.addMember(memberId, ProjectRole.VIEWER, ownerId))
                .isInstanceOf(DuplicateProjectMemberException.class);
    }

    @Test
    void addMember_by_non_owner_non_manager_throws_UnauthorizedProjectAccessException() {
        UUID ownerId = UUID.randomUUID();
        UUID contributor = UUID.randomUUID();
        UUID newMember = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));
        project.addMember(contributor, ProjectRole.CONTRIBUTOR, ownerId);

        assertThatThrownBy(() -> project.addMember(newMember, ProjectRole.VIEWER, contributor))
                .isInstanceOf(UnauthorizedProjectAccessException.class);
    }

    @Test
    void addMember_by_manager_succeeds() {
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID newMemberId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));
        project.addMember(managerId, ProjectRole.MANAGER, ownerId);

        project.addMember(newMemberId, ProjectRole.VIEWER, managerId);

        assertThat(project.isMember(newMemberId)).isTrue();
    }

    @Test
    void removeMember_marks_member_inactive() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));
        project.addMember(memberId, ProjectRole.CONTRIBUTOR, ownerId);

        project.removeMember(memberId);

        assertThat(project.isMember(memberId)).isFalse();
    }

    @Test
    void isMember_returns_true_for_active_member_and_false_for_non_member() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));
        project.addMember(memberId, ProjectRole.CONTRIBUTOR, ownerId);

        assertThat(project.isMember(memberId)).isTrue();
        assertThat(project.isMember(stranger)).isFalse();
    }

    @Test
    void hasRole_returns_true_for_matching_role_and_false_otherwise() {
        UUID ownerId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));

        assertThat(project.hasRole(ownerId, ProjectRole.OWNER)).isTrue();
        assertThat(project.hasRole(ownerId, ProjectRole.CONTRIBUTOR)).isFalse();
        assertThat(project.hasRole(ownerId, ProjectRole.OWNER, ProjectRole.MANAGER)).isTrue();
    }

    // ── canBeAccessedBy() tests — TEAM visibility semantics ───────────────

    @Test
    void canBeAccessedBy_PUBLIC_allows_any_caller() {
        UUID ownerId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PUBLIC, ownerId, UUID.randomUUID()));
        project.pullDomainEvents();

        assertThat(project.canBeAccessedBy(stranger, Set.of())).isTrue();
        assertThat(project.canBeAccessedBy(stranger, null)).isTrue();
    }

    @Test
    void canBeAccessedBy_PRIVATE_allows_only_members() {
        UUID ownerId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.PRIVATE, ownerId, UUID.randomUUID()));
        project.pullDomainEvents();

        assertThat(project.canBeAccessedBy(ownerId, Set.of())).isTrue();   // direct member
        assertThat(project.canBeAccessedBy(stranger, Set.of())).isFalse(); // not a member
    }

    @Test
    void canBeAccessedBy_TEAM_allows_direct_members_regardless_of_team() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.TEAM, ownerId, tenantId));
        project.pullDomainEvents();

        assertThat(project.canBeAccessedBy(ownerId, Set.of())).isTrue(); // direct member
    }

    @Test
    void canBeAccessedBy_TEAM_allows_user_in_assigned_active_team() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID outsiderWithTeam = UUID.randomUUID();

        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.TEAM, ownerId, tenantId));
        project.pullDomainEvents();
        project.assignTeam(teamId, ownerId);
        project.pullDomainEvents();

        // outsider whose callerTeamIds includes the assigned team
        assertThat(project.canBeAccessedBy(outsiderWithTeam, Set.of(teamId))).isTrue();
    }

    @Test
    void canBeAccessedBy_TEAM_denies_user_NOT_in_assigned_team() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID assignedTeamId = UUID.randomUUID();
        UUID unrelatedTeamId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.TEAM, ownerId, tenantId));
        project.pullDomainEvents();
        project.assignTeam(assignedTeamId, ownerId);
        project.pullDomainEvents();

        // stranger's teams do not overlap with the project's assigned teams
        assertThat(project.canBeAccessedBy(stranger, Set.of(unrelatedTeamId))).isFalse();
        assertThat(project.canBeAccessedBy(stranger, Set.of())).isFalse();
    }

    @Test
    void canBeAccessedBy_TEAM_denies_user_in_orphaned_team() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        Project project = Project.create(new CreateProjectCommand(
                "Alpha Project", null, ProjectVisibility.TEAM, ownerId, tenantId));
        project.pullDomainEvents();
        project.assignTeam(teamId, ownerId);
        project.pullDomainEvents();
        project.removeTeam(teamId); // orphan the team (simulates team deletion)

        // outsider's team is now orphaned — must not grant access
        assertThat(project.canBeAccessedBy(outsider, Set.of(teamId))).isFalse();
    }
}
