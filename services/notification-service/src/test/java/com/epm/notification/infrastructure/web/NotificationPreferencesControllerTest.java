package com.epm.notification.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationPreference;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.in.GetPreferencesUseCase;
import com.epm.notification.domain.port.in.UpdatePreferenceUseCase;
import com.epm.notification.infrastructure.adapter.in.web.GlobalExceptionHandler;
import com.epm.notification.infrastructure.adapter.in.web.NotificationPreferencesController;
import com.epm.notification.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @WebMvcTest for NotificationPreferencesController (TDD — Strict).
 *
 * <p>Verifies GET preferences list, PUT preference update, and 401 on unauthenticated access.
 */
@WebMvcTest(NotificationPreferencesController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
    "spring.config.import=",
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwks-uri=https://example.com/.well-known/jwks.json",
    "eureka.client.enabled=false"
})
class NotificationPreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetPreferencesUseCase getPreferencesUseCase;

    @MockitoBean
    private UpdatePreferenceUseCase updatePreferenceUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    // ── GET /api/v1/notifications/preferences — list for authenticated user ──

    @Test
    void getPreferences_withValidJwt_returnsPreferenceList() throws Exception {
        NotificationPreference pref1 = NotificationPreference.create(
                userId, tenantId, NotificationType.TASK_ASSIGNED, NotificationChannel.IN_APP, true);
        NotificationPreference pref2 = NotificationPreference.create(
                userId, tenantId, NotificationType.PROJECT_CREATED, NotificationChannel.EMAIL, false);

        when(getPreferencesUseCase.getPreferences(userId, tenantId))
                .thenReturn(List.of(pref1, pref2));

        mockMvc.perform(get("/api/v1/notifications/preferences")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventType").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$[0].channel").value("IN_APP"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[1].eventType").value("PROJECT_CREATED"))
                .andExpect(jsonPath("$[1].channel").value("EMAIL"))
                .andExpect(jsonPath("$[1].enabled").value(false));
    }

    @Test
    void getPreferences_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/v1/notifications/preferences/{eventType}/{channel} ──────────

    @Test
    void updatePreference_withValidJwtAndBody_returns200() throws Exception {
        doNothing().when(updatePreferenceUseCase).updatePreference(
                eq(userId), eq(tenantId),
                eq(NotificationType.TASK_ASSIGNED), eq(NotificationChannel.IN_APP),
                eq(false));

        mockMvc.perform(put("/api/v1/notifications/preferences/TASK_ASSIGNED/IN_APP")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        verify(updatePreferenceUseCase).updatePreference(
                eq(userId), eq(tenantId),
                eq(NotificationType.TASK_ASSIGNED), eq(NotificationChannel.IN_APP),
                eq(false));
    }

    @Test
    void updatePreference_withInvalidEventType_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/preferences/INVALID_TYPE/IN_APP")
                        .with(jwt().jwt(j -> j
                                .subject(userId.toString())
                                .claim("tenant_id", tenantId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isBadRequest());
    }
}
