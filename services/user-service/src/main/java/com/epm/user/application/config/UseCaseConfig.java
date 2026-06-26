package com.epm.user.application.config;

import com.epm.user.application.usecase.AddTeamMemberUseCaseImpl;
import com.epm.user.application.usecase.CreateTeamUseCaseImpl;
import com.epm.user.application.usecase.DeleteOwnProfileUseCaseImpl;
import com.epm.user.application.usecase.DeleteTeamUseCaseImpl;
import com.epm.user.application.usecase.GetOwnProfileUseCaseImpl;
import com.epm.user.application.usecase.GetTeamUseCaseImpl;
import com.epm.user.application.usecase.ListTeamsUseCaseImpl;
import com.epm.user.application.usecase.ListTenantUsersUseCaseImpl;
import com.epm.user.application.usecase.RemoveTeamMemberUseCaseImpl;
import com.epm.user.application.usecase.UpdateOwnProfileUseCaseImpl;
import com.epm.user.domain.port.in.DeleteOwnProfileUseCase;
import com.epm.user.domain.port.out.TeamRepository;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
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
            TransactionalOutboxWriter outboxWriter) {
        return new UpdateOwnProfileUseCaseImpl(profileRepository, outboxWriter);
    }

    @Bean
    DeleteOwnProfileUseCase deleteOwnProfileUseCase(UserProfileRepository profileRepository,
            TransactionalOutboxWriter outboxWriter) {
        return new DeleteOwnProfileUseCaseImpl(profileRepository, outboxWriter);
    }

    @Bean
    CreateTeamUseCaseImpl createTeamUseCase(TransactionalOutboxWriter outboxWriter) {
        return new CreateTeamUseCaseImpl(outboxWriter);
    }

    @Bean
    ListTeamsUseCaseImpl listTeamsUseCase(TeamRepository teamRepository) {
        return new ListTeamsUseCaseImpl(teamRepository);
    }

    @Bean
    GetTeamUseCaseImpl getTeamUseCase(TeamRepository teamRepository,
            UserProfileRepository profileRepository) {
        return new GetTeamUseCaseImpl(teamRepository, profileRepository);
    }

    @Bean
    AddTeamMemberUseCaseImpl addTeamMemberUseCase(TeamRepository teamRepository,
            UserProfileRepository profileRepository,
            TransactionalOutboxWriter outboxWriter) {
        return new AddTeamMemberUseCaseImpl(teamRepository, profileRepository, outboxWriter);
    }

    @Bean
    RemoveTeamMemberUseCaseImpl removeTeamMemberUseCase(TeamRepository teamRepository,
            TransactionalOutboxWriter outboxWriter) {
        return new RemoveTeamMemberUseCaseImpl(teamRepository, outboxWriter);
    }

    @Bean
    DeleteTeamUseCaseImpl deleteTeamUseCase(TeamRepository teamRepository,
            TransactionalOutboxWriter outboxWriter) {
        return new DeleteTeamUseCaseImpl(teamRepository, outboxWriter);
    }
}
