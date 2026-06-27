package com.epm.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TeamMembership#changeRole(TeamRole)}.
 */
class TeamMembershipTest {

    @Test
    void changeRole_fromMemberToViewer_updatesRole() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMembership membership = TeamMembership.create(teamId, userId, TeamRole.MEMBER);

        membership.changeRole(TeamRole.VIEWER);

        assertThat(membership.getRole()).isEqualTo(TeamRole.VIEWER);
    }

    @Test
    void changeRole_fromViewerToMember_updatesRole() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMembership membership = TeamMembership.create(teamId, userId, TeamRole.VIEWER);

        membership.changeRole(TeamRole.MEMBER);

        assertThat(membership.getRole()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void changeRole_toOwner_throwsIllegalArgumentException() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMembership membership = TeamMembership.create(teamId, userId, TeamRole.MEMBER);

        assertThatThrownBy(() -> membership.changeRole(TeamRole.OWNER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OWNER");
    }
}
