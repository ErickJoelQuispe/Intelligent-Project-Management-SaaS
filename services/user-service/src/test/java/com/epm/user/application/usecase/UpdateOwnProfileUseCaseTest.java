package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.OptimisticLockException;
import com.epm.user.domain.exception.ProfileNotFoundException;
import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.in.command.UpdateProfileCommand;
import com.epm.user.domain.port.in.result.UserProfileResult;
import com.epm.user.domain.port.out.DomainEventPublisher;
import com.epm.user.domain.port.out.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UpdateOwnProfileUseCaseImpl}.
 * RED: UpdateOwnProfileUseCaseImpl does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class UpdateOwnProfileUseCaseTest {

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private UpdateOwnProfileUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateOwnProfileUseCaseImpl(profileRepository, eventPublisher);
    }

    @Test
    void happyPathUpdatesSavesAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "alice@example.com", "Alice", "Smith");
        when(profileRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileCommand cmd = new UpdateProfileCommand("Bob", "Jones", "Bio", "http://img.png", 0);
        UserProfileResult result = useCase.updateProfile(userId, tenantId, cmd);

        assertThat(result.firstName()).isEqualTo("Bob");
        assertThat(result.lastName()).isEqualTo("Jones");
        assertThat(result.version()).isEqualTo(1L);
        verify(profileRepository).save(any());
        verify(eventPublisher).publish(any());
    }

    @Test
    void versionMismatchThrowsOptimisticLockException() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "alice@example.com", "Alice", "Smith");
        when(profileRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(profile));

        // Profile is at version 0, but command sends version=5 (mismatch)
        UpdateProfileCommand cmd = new UpdateProfileCommand("Bob", "Jones", null, null, 5);
        assertThatThrownBy(() -> useCase.updateProfile(userId, tenantId, cmd))
                .isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void emailFieldIsNotChangedByUpdate() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "alice@example.com", "Alice", "Smith");
        when(profileRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileCommand cmd = new UpdateProfileCommand("Bob", "Jones", null, null, 0);
        UserProfileResult result = useCase.updateProfile(userId, tenantId, cmd);

        assertThat(result.email()).isEqualTo("alice@example.com");
    }

    @Test
    void profileNotFoundThrowsProfileNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(profileRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.empty());

        UpdateProfileCommand cmd = new UpdateProfileCommand("Bob", "Jones", null, null, 0);
        assertThatThrownBy(() -> useCase.updateProfile(userId, tenantId, cmd))
                .isInstanceOf(ProfileNotFoundException.class);
    }
}
