package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.epm.user.domain.model.Team;
import com.epm.user.domain.port.in.result.TeamResult;
import com.epm.user.domain.port.out.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ListTeamsUseCaseImpl}.
 * RED: ListTeamsUseCaseImpl does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class ListTeamsUseCaseTest {

    @Mock
    private TeamRepository teamRepository;

    private ListTeamsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListTeamsUseCaseImpl(teamRepository);
    }

    @Test
    void returnsAllTeamsWherUserIsMember() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Team team1 = Team.create(tenantId, userId, "Alpha", null);
        Team team2 = Team.create(tenantId, userId, "Beta", null);
        when(teamRepository.findAllByMemberUserId(userId, tenantId)).thenReturn(List.of(team1, team2));

        List<TeamResult> results = useCase.listTeams(userId, tenantId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).name()).isEqualTo("Alpha");
        assertThat(results.get(1).name()).isEqualTo("Beta");
    }

    @Test
    void returnsEmptyListWhenNoMemberships() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(teamRepository.findAllByMemberUserId(userId, tenantId)).thenReturn(List.of());

        List<TeamResult> results = useCase.listTeams(userId, tenantId);

        assertThat(results).isEmpty();
    }
}
