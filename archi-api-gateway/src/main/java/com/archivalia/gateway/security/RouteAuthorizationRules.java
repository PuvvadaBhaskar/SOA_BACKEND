package com.archivalia.gateway.security;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Central, ordered authorization table for every routed request.
 * The first matching rule wins. Anything that matches no rule is ADMIN-only (deny by default).
 *
 * <pre>
 * Auth   : POST /auth/register, POST /auth/login       -> public
 *          everything else under /auth/**              -> any authenticated user
 * Book   : GET  /books/**                              -> USER, ADMIN
 *          POST / PUT / DELETE /books/**               -> ADMIN
 * Borrow : POST /borrows                               -> USER, ADMIN (own borrow, userId from token)
 *          PUT  /borrows/{id}/return|extend            -> USER, ADMIN (ownership checked in borrow-service)
 *          GET  /borrows  (list all)                   -> ADMIN
 *          GET  /borrows/**                            -> USER, ADMIN (ownership checked in borrow-service)
 *          DELETE /borrows/**                          -> ADMIN
 * Fine   : GET  /fines    (list all)                   -> ADMIN
 *          GET  /fines/**                              -> USER, ADMIN (owner-or-admin checked in fine-service)
 *          POST / PUT / DELETE /fines/**               -> ADMIN
 * </pre>
 */
@Component
public class RouteAuthorizationRules {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";

    private static final Set<String> ANY_ROLE = Set.of(USER, ADMIN);
    private static final Set<String> ADMIN_ONLY = Set.of(ADMIN);

    private final List<Rule> publicEndpoints;
    private final List<Rule> rules;

    public RouteAuthorizationRules() {

        this.publicEndpoints = List.of(
                rule(HttpMethod.POST, "/auth/register", Set.of()),
                rule(HttpMethod.POST, "/auth/login", Set.of()));

        this.rules = List.of(
                // ---- auth-service ----
                rule(null, "/auth/**", ANY_ROLE),

                // ---- book-service ----
                rule(HttpMethod.GET, "/books/**", ANY_ROLE),
                rule(HttpMethod.POST, "/books/**", ADMIN_ONLY),
                rule(HttpMethod.PUT, "/books/**", ADMIN_ONLY),
                rule(HttpMethod.DELETE, "/books/**", ADMIN_ONLY),

                // ---- borrow-service ----
                rule(HttpMethod.POST, "/borrows", ANY_ROLE),
                rule(HttpMethod.PUT, "/borrows/{id}/return", ANY_ROLE),
                rule(HttpMethod.PUT, "/borrows/{id}/extend", ANY_ROLE),
                rule(HttpMethod.GET, "/borrows", ADMIN_ONLY),
                rule(HttpMethod.GET, "/borrows/**", ANY_ROLE),
                rule(HttpMethod.DELETE, "/borrows/**", ADMIN_ONLY),

                // ---- fine-service ----
                rule(HttpMethod.GET, "/fines", ADMIN_ONLY),
                rule(HttpMethod.GET, "/fines/**", ANY_ROLE),
                rule(HttpMethod.POST, "/fines/**", ADMIN_ONLY),
                rule(HttpMethod.PUT, "/fines/**", ADMIN_ONLY),
                rule(HttpMethod.DELETE, "/fines/**", ADMIN_ONLY));
    }

    /** True for the endpoints that must be reachable without a token. */
    public boolean isPublic(HttpMethod method, String path) {
        return publicEndpoints.stream().anyMatch(r -> r.matches(method, path));
    }

    /** True when the given role may call METHOD path. Unmatched requests are ADMIN-only. */
    public boolean isAllowed(HttpMethod method, String path, String role) {
        if (role == null) {
            return false;
        }
        Set<String> allowed = rules.stream()
                .filter(r -> r.matches(method, path))
                .findFirst()
                .map(Rule::roles)
                .orElse(ADMIN_ONLY);
        return allowed.contains(role.toUpperCase());
    }

    private static Rule rule(HttpMethod method, String pattern, Set<String> roles) {
        return new Rule(method, PathPatternParser.defaultInstance.parse(pattern), roles);
    }

    /** method == null means "any HTTP method". */
    private record Rule(HttpMethod method, PathPattern pattern, Set<String> roles) {

        boolean matches(HttpMethod requestMethod, String path) {
            return (method == null || method.equals(requestMethod))
                    && pattern.matches(PathContainer.parsePath(path));
        }
    }
}
