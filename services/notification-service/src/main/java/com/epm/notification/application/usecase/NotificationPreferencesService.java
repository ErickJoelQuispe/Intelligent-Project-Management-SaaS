package com.epm.notification.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.epm.notification.domain.model.NotificationChannel;
import com.epm.notification.domain.model.NotificationPreference;
import com.epm.notification.domain.model.NotificationType;
import com.epm.notification.domain.port.in.GetPreferencesUseCase;
import com.epm.notification.domain.port.in.UpdatePreferenceUseCase;
import com.epm.notification.domain.port.out.NotificationPreferenceRepository;

/**
 * Application service implementing both preference use cases.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 *
 * <p>{@code getPreferences()} returns the full matrix (all types × all channels),
 * defaulting absent rows to {@code enabled=true} (opt-out model).
 * {@code updatePreference()} delegates directly to the repository upsert.
 */
public class NotificationPreferencesService
        implements GetPreferencesUseCase, UpdatePreferenceUseCase {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferencesService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    @Override
    public List<NotificationPreference> getPreferences(UUID userId, UUID tenantId) {
        List<NotificationPreference> saved = preferenceRepository.findAllByUserId(userId);

        // Build a lookup map for quick access
        Map<String, NotificationPreference> savedMap = saved.stream()
                .collect(Collectors.toMap(
                        p -> key(p.getEventType(), p.getChannel()),
                        Function.identity()));

        // Return full matrix: all types × all channels, defaulting missing to enabled=true
        List<NotificationPreference> result = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            for (NotificationChannel channel : NotificationChannel.values()) {
                String k = key(type, channel);
                if (savedMap.containsKey(k)) {
                    result.add(savedMap.get(k));
                } else {
                    // Default: enabled=true (opt-out model)
                    result.add(NotificationPreference.create(userId, tenantId, type, channel, true));
                }
            }
        }
        return result;
    }

    @Override
    public void updatePreference(UUID userId, UUID tenantId,
            NotificationType eventType, NotificationChannel channel, boolean enabled) {
        NotificationPreference pref = NotificationPreference.create(userId, tenantId, eventType, channel, enabled);
        preferenceRepository.upsert(pref);
    }

    private String key(NotificationType type, NotificationChannel channel) {
        return type.name() + ":" + channel.name();
    }
}
