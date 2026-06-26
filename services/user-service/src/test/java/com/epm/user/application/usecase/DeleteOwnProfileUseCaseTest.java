package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.exception.ProfileNotFoundException;
import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.out.TransactionalOutboxWriter;
import com.epm.user.domain.port.out.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DeleteOwnProfileUseCaseImpl}.
 *
 * <p>Strict TDD — RED written first. Tests verify:
 * - softDelete() is called on the profile (deletedAt is set)
 * - The profile is saved via outboxWriter
 * - ProfileNotFoundException is thrown for unknown userId
 */
class DeleteOwnProfileUseCaseTest {

    private UserProfileRepository profileRepository;
    private TransactionalOutboxWriter outboxWriter;
    private DeleteOwnProfileUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        profileRepository = mock(UserProfileRepository.class);
        outboxWriter = mock(TransactionalOutboxWriter.class);
        useCase = new DeleteOwnProfileUseCaseImpl(profileRepository, outboxWriter);
    }

    // ── RED: softDelete is called and profile is saved ──────────────────────

    @Test
    void execute_softDeletesProfileAndSaves() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "alice@example.com", "Alice", "Smith");

        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(outboxWriter.saveProfileAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(userId);

        // deletedAt must be set after execute()
        assertThat(profile.getDeletedAt()).isNotNull();
        verify(outboxWriter).saveProfileAndPublish(profile);
    }

    // ── TRIANGULATE: different userId also soft-deletes ──────────────────────

    @Test
    void execute_withDifferentUserId_softDeletesProfile() {
        UUID userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "bob@example.com", "Bob", "Jones");

        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(outboxWriter.saveProfileAndPublish(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(userId);

        assertThat(profile.getDeletedAt()).isNotNull();
        verify(outboxWriter).saveProfileAndPublish(profile);
    }

    // ── Not found: throws ProfileNotFoundException ────────────────────────────

    @Test
    void execute_whenProfileNotFound_throwsProfileNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(userId))
                .isInstanceOf(ProfileNotFoundException.class);

        verify(outboxWriter, never()).saveProfileAndPublish(any());
    }
}
