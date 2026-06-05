package com.epm.ai.infrastructure.security;

import java.util.UUID;

/**
 * ThreadLocal holder for the current request's tenant UUID.
 *
 * <p>Set by {@link TenantInterceptor#preHandle} from the JWT {@code tenant_id} claim.
 * Cleared by {@link TenantInterceptor#afterCompletion} to avoid memory leaks.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();

    private TenantContextHolder() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void set(UUID tenantId) {
        TENANT.set(tenantId);
    }

    public static UUID get() {
        return TENANT.get();
    }

    public static void clear() {
        TENANT.remove();
    }
}
