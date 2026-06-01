package com.epm.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit test for {@link TenantInterceptor}.
 *
 * RED: Fails because TenantInterceptor and TenantContextHolder don't exist yet.
 */
class TenantInterceptorTest {

    private TenantInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new TenantInterceptor();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void preHandleSetsTenantIdFromJwtClaim() throws Exception {
        // Arrange: build a mock JWT with tenant_id claim
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("tenant_id")).thenReturn(tenantId.toString());
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        boolean result = interceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(result).isTrue();
        assertThat(TenantContextHolder.get()).isEqualTo(tenantId);
    }

    @Test
    void preHandleWithNoAuthenticationDoesNotThrow() throws Exception {
        // Arrange: no authentication set in SecurityContext
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act — must not throw
        boolean result = interceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(result).isTrue();
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void afterCompletionClearsTenantContextHolder() throws Exception {
        // Arrange: set tenant in holder
        UUID tenantId = UUID.randomUUID();
        TenantContextHolder.set(tenantId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        interceptor.afterCompletion(request, response, new Object(), null);

        // Assert: holder is cleared
        assertThat(TenantContextHolder.get()).isNull();
    }
}
