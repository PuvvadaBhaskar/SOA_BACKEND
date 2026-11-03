package com.archivalia.borrow.client;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.archivalia.borrow.security.GatewayAuthenticationFilter;
import com.archivalia.borrow.security.GatewayUser;

/**
 * Builds the headers forwarded on Feign calls so that book-service / fine-service accept the call:
 * the caller's identity (X-User-*) plus the shared X-Gateway-Secret.
 */
@Component
public class InternalHeaders {

    /** Identity used by background jobs (no end-user request in flight). */
    public static final GatewayUser SYSTEM = new GatewayUser(0L, "borrow-service", GatewayUser.ROLE_SERVICE);

    private final String gatewaySecret;

    public InternalHeaders(@Value("${gateway.shared-secret}") String gatewaySecret) {
        this.gatewaySecret = gatewaySecret;
    }

    public Map<String, String> forUser(GatewayUser user) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(GatewayAuthenticationFilter.HEADER_USER_ID, String.valueOf(user.id()));
        headers.put(GatewayAuthenticationFilter.HEADER_USER_NAME, user.username());
        headers.put(GatewayAuthenticationFilter.HEADER_USER_ROLE, user.role());
        headers.put(GatewayAuthenticationFilter.HEADER_GATEWAY_SECRET, gatewaySecret);
        return headers;
    }

    public Map<String, String> forSystem() {
        return forUser(SYSTEM);
    }
}
