package com.epm.notification.application.usecase;

import java.time.LocalDateTime;
import java.util.UUID;

import com.epm.notification.domain.model.UserEmailCache;
import com.epm.notification.domain.port.in.CacheUserEmailUseCase;
import com.epm.notification.domain.port.out.UserEmailCacheRepository;

/**
 * Application service implementing {@link CacheUserEmailUseCase}.
 *
 * <p>Pure Java — no Spring annotations. Wired by {@code UseCaseConfig}.
 */
public class CacheUserEmailService implements CacheUserEmailUseCase {

    private final UserEmailCacheRepository userEmailCacheRepository;

    public CacheUserEmailService(UserEmailCacheRepository userEmailCacheRepository) {
        this.userEmailCacheRepository = userEmailCacheRepository;
    }

    @Override
    public void cacheUserEmail(UUID userId, UUID tenantId, String email) {
        UserEmailCache entry = new UserEmailCache(userId, tenantId, email, LocalDateTime.now());
        userEmailCacheRepository.save(entry);
    }
}
