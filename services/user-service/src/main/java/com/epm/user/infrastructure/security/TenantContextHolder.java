package com.epm.user.infrastructure.security;

import java.util.UUID;

/**
 * ThreadLocal holder for the current request's tenant UUID.
 *
 * <p>Set by {@link TenantInterceptor#preHandle} from the JWT {@code tenant_id} claim.
 * Cleared by {@link TenantInterceptor#afterCompletion} to avoid memory leaks.
 *
 * <p>This class is intentionally non-instantiable (utility class).
 */
public final class TenantContextHolder {

    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();

    private TenantContextHolder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Sets the tenant UUID for the current thread. */
    public static void set(UUID tenantId) {
        TENANT.set(tenantId);
    }

    /** Returns the tenant UUID for the current thread, or {@code null} if not set. */
    public static UUID get() {
        return TENANT.get();
    }

    /** Clears the tenant UUID from the current thread. Must be called in request cleanup. */
    public static void clear() {
        TENANT.remove();
    }
}
