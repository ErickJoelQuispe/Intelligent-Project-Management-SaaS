package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.InvalidPreferencesException;
import com.epm.user.domain.exception.OptimisticLockException;
import com.epm.user.domain.exception.ProfileNotFoundException;
import com.epm.user.domain.model.UserPreferences;
import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.in.command.UpdateProfileCommand;
import com.epm.user.domain.port.in.result.UserProfileResult;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import com.epm.user.domain.port.out.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UpdateOwnProfileUseCaseImpl}.
 */
@ExtendWith(MockitoExtension.class)
class UpdateOwnProfileUseCaseTest {

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private TransactionalOutboxWriter outboxWriter;

    private UpdateOwnProfileUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateOwnProfileUseCaseImpl(profileRepository, outboxWriter);
    }

    @Test
    void happyPathUpdatesSavesAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "alice@example.com", "Alice", "Smith");
        when(profileRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(profile));
        when(outboxWriter.saveProfileAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileCommand cmd = new UpdateProfileCommand("Bob", "Jones", "Bio", "http://img.png", 0);
        UserProfileResult result = useCase.updateProfile(userId, tenantId, cmd);

        assertThat(result.firstName()).isEqualTo("Bob");
        assertThat(result.lastName()).isEqualTo("Jones");
        assertThat(result.version()).isEqualTo(1L);
        verify(outboxWriter).saveProfileAndPublish(any());
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
        when(outboxWriter.saveProfileAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

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

    @Test
    void invalidPreferencesThrowsBeforeSaving() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "alice@example.com", "Alice", "Smith");
        when(profileRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(profile));

        UserPreferences invalid = new UserPreferences("fr", "UTC", "ISO", "MONDAY"); // "fr" is invalid
        UpdateProfileCommand cmd = new UpdateProfileCommand("Alice", "Smith", null, null, 0, invalid);

        assertThatThrownBy(() -> useCase.updateProfile(userId, tenantId, cmd))
                .isInstanceOf(InvalidPreferencesException.class);

        // outboxWriter must NOT have been called — validation failed before save
        verify(outboxWriter, org.mockito.Mockito.never()).saveProfileAndPublish(any());
    }

    @Test
    void validPreferencesSavedWithProfile() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "alice@example.com", "Alice", "Smith");
        when(profileRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(profile));
        when(outboxWriter.saveProfileAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        UserPreferences prefs = new UserPreferences("es", "UTC", "DD/MM/YYYY", "SUNDAY");
        UpdateProfileCommand cmd = new UpdateProfileCommand("Alice", "Smith", null, null, 0, prefs);
        UserProfileResult result = useCase.updateProfile(userId, tenantId, cmd);

        assertThat(result.preferences()).isNotNull();
        assertThat(result.preferences().language()).isEqualTo("es");
    }
}
