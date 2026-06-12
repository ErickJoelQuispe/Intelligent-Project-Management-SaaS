package com.epm.user.application.config;

import com.epm.user.application.usecase.AddTeamMemberUseCaseImpl;
import com.epm.user.application.usecase.CreateTeamUseCaseImpl;
import com.epm.user.application.usecase.DeleteTeamUseCaseImpl;
import com.epm.user.application.usecase.GetOwnProfileUseCaseImpl;
import com.epm.user.application.usecase.GetTeamUseCaseImpl;
import com.epm.user.application.usecase.ListTeamsUseCaseImpl;
import com.epm.user.application.usecase.ListTenantUsersUseCaseImpl;
import com.epm.user.application.usecase.RemoveTeamMemberUseCaseImpl;
import com.epm.user.application.usecase.UpdateOwnProfileUseCaseImpl;
import com.epm.user.domain.port.out.DomainEventPublisher;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.UserProfileRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that wires use case implementations to their port interfaces.
 *
 * <p>Use case implementations are pure Java (no Spring annotations).
 * This configuration class is the only place they are coupled to Spring.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    GetOwnProfileUseCaseImpl getOwnProfileUseCase(UserProfileRepository profileRepository) {
        return new GetOwnProfileUseCaseImpl(profileRepository);
    }

    @Bean
    ListTenantUsersUseCaseImpl listTenantUsersUseCase(UserProfileRepository profileRepository) {
        return new ListTenantUsersUseCaseImpl(profileRepository);
    }

    @Bean
    UpdateOwnProfileUseCaseImpl updateOwnProfileUseCase(UserProfileRepository profileRepository,
            DomainEventPublisher eventPublisher) {
        return new UpdateOwnProfileUseCaseImpl(profileRepository, eventPublisher);
    }

    @Bean
    CreateTeamUseCaseImpl createTeamUseCase(TeamRepository teamRepository,
            DomainEventPublisher eventPublisher) {
        return new CreateTeamUseCaseImpl(teamRepository, eventPublisher);
    }

    @Bean
    ListTeamsUseCaseImpl listTeamsUseCase(TeamRepository teamRepository) {
        return new ListTeamsUseCaseImpl(teamRepository);
    }

    @Bean
    GetTeamUseCaseImpl getTeamUseCase(TeamRepository teamRepository) {
        return new GetTeamUseCaseImpl(teamRepository);
    }

    @Bean
    AddTeamMemberUseCaseImpl addTeamMemberUseCase(TeamRepository teamRepository,
            UserProfileRepository profileRepository,
            DomainEventPublisher eventPublisher) {
        return new AddTeamMemberUseCaseImpl(teamRepository, profileRepository, eventPublisher);
    }

    @Bean
    RemoveTeamMemberUseCaseImpl removeTeamMemberUseCase(TeamRepository teamRepository,
            DomainEventPublisher eventPublisher) {
        return new RemoveTeamMemberUseCaseImpl(teamRepository, eventPublisher);
    }

    @Bean
    DeleteTeamUseCaseImpl deleteTeamUseCase(TeamRepository teamRepository,
            DomainEventPublisher eventPublisher) {
        return new DeleteTeamUseCaseImpl(teamRepository, eventPublisher);
    }
}
