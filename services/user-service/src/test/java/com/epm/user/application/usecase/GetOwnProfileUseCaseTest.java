package com.epm.user.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.epm.user.domain.model.UserProfile;
import com.epm.user.domain.port.in.dto.JwtClaimsDto;
import com.epm.user.domain.port.in.result.UserProfileResult;
import com.epm.user.domain.port.out.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GetOwnProfileUseCaseImpl}.
 * RED: GetOwnProfileUseCaseImpl does not exist yet.
 */
@ExtendWith(MockitoExtension.class)
class GetOwnProfileUseCaseTest {

    @Mock
    private UserProfileRepository profileRepository;

    private GetOwnProfileUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOwnProfileUseCaseImpl(profileRepository);
    }

    @Test
    void profileInDatabaseReturnsResultWithProvisionalFalse() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, tenantId, "alice@example.com", "Alice", "Smith");
        when(profileRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(profile));

        JwtClaimsDto claims = new JwtClaimsDto(userId, tenantId, "alice@example.com", "Alice", "Smith");
        UserProfileResult result = useCase.getProfile(userId, tenantId, claims);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.provisional()).isFalse();
    }

    @Test
    void profileNotInDatabaseReturnsProvisionalResultFromJwtClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(profileRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.empty());

        JwtClaimsDto claims = new JwtClaimsDto(userId, tenantId, "alice@example.com", "Alice", "Smith");
        UserProfileResult result = useCase.getProfile(userId, tenantId, claims);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.firstName()).isEqualTo("Alice");
        assertThat(result.provisional()).isTrue();
    }

    @Test
    void wrongTenantReturnsProvisionalResult() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID wrongTenant = UUID.randomUUID();
        // Profile exists but not for this tenant
        when(profileRepository.findByIdAndTenantId(userId, wrongTenant)).thenReturn(Optional.empty());

        JwtClaimsDto claims = new JwtClaimsDto(userId, wrongTenant, "alice@example.com", "Alice", "Smith");
        UserProfileResult result = useCase.getProfile(userId, wrongTenant, claims);

        assertThat(result.provisional()).isTrue();
    }
}
