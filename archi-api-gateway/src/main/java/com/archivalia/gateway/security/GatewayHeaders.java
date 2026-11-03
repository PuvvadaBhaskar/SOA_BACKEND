package com.archivalia.gateway.security;

import java.util.List;

/**
 * Header names shared between the gateway and the downstream services.
 */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_NAME = "X-User-Name";
    public static final String USER_ROLE = "X-User-Role";
    public static final String GATEWAY_SECRET = "X-Gateway-Secret";

    /** Identity headers that only the gateway may set. Client-supplied copies are always removed. */
    public static final List<String> TRUSTED_HEADERS = List.of(USER_ID, USER_NAME, USER_ROLE, GATEWAY_SECRET);

    private GatewayHeaders() {
    }
}
