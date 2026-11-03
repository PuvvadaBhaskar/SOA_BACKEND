package com.archivalia.borrow.security;

/**
 * Caller identity forwarded by the API gateway (X-User-Id / X-User-Name / X-User-Role).
 * Available in controllers via {@code @AuthenticationPrincipal GatewayUser user}.
 */
public record GatewayUser(Long id, String username, String role) {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    /** Used for internal, non-user calls (e.g. scheduled jobs in borrow-service). */
    public static final String ROLE_SERVICE = "SERVICE";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean owns(Long userId) {
        return id != null && id.equals(userId);
    }
}
