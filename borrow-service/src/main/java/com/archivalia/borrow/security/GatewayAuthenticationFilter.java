package com.archivalia.borrow.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Defence in depth: trusts the X-User-* identity headers ONLY when the request also carries the
 * shared X-Gateway-Secret that the API gateway (and internal Feign calls) add. A client calling this
 * service directly cannot forge an identity because it does not know the secret.
 * Requests without a valid identity stay unauthenticated and are answered with 401 by the entry point.
 */
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";

    /** Request attribute holding the reason a request was not authenticated (used in the 401 body). */
    public static final String REJECTION_REASON = GatewayAuthenticationFilter.class.getName() + ".reason";

    private static final Set<String> KNOWN_ROLES =
            Set.of(GatewayUser.ROLE_USER, GatewayUser.ROLE_ADMIN, GatewayUser.ROLE_SERVICE);

    private final byte[] expectedSecret;

    public GatewayAuthenticationFilter(String gatewaySecret) {
        this.expectedSecret = gatewaySecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String secret = request.getHeader(HEADER_GATEWAY_SECRET);

        if (secret == null) {
            request.setAttribute(REJECTION_REASON,
                    "Missing gateway credentials - this service must be called through the API gateway");
        } else if (!MessageDigest.isEqual(expectedSecret, secret.getBytes(StandardCharsets.UTF_8))) {
            request.setAttribute(REJECTION_REASON, "Invalid gateway credentials");
        } else {
            GatewayUser user = parseUser(request);
            if (user == null) {
                request.setAttribute(REJECTION_REASON, "Missing or malformed X-User-Id / X-User-Name / X-User-Role headers");
            } else {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            }
        }

        chain.doFilter(request, response);
    }

    private static GatewayUser parseUser(HttpServletRequest request) {
        String id = request.getHeader(HEADER_USER_ID);
        String name = request.getHeader(HEADER_USER_NAME);
        String role = request.getHeader(HEADER_USER_ROLE);

        if (id == null || name == null || name.isBlank() || role == null) {
            return null;
        }
        String normalizedRole = role.trim().toUpperCase();
        if (!KNOWN_ROLES.contains(normalizedRole)) {
            return null;
        }
        try {
            return new GatewayUser(Long.parseLong(id.trim()), name.trim(), normalizedRole);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
