
package com.archivalia.gateway.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;
    private final RouteAuthorizationRules rules;
    private final String gatewaySecret;

    public JwtAuthenticationFilter(
            JwtValidator jwtValidator,
            RouteAuthorizationRules rules,
            @Value("${gateway.shared-secret}") String gatewaySecret) {

        this.jwtValidator = jwtValidator;
        this.rules = rules;
        this.gatewaySecret = gatewaySecret;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getPath()
                .pathWithinApplication()
                .value();

        // Reject suspicious paths
        if (isSuspiciousPath(request)) {
            return writeError(
                    exchange,
                    HttpStatus.BAD_REQUEST,
                    "Invalid request path");
        }

        // Allow Swagger UI and OpenAPI documentation without JWT.
        // Inject trusted gateway credentials for downstream services.
        if (isSwaggerPath(path)) {

            ServerHttpRequest swaggerRequest = request.mutate()
                    .headers(headers -> {
                        GatewayHeaders.TRUSTED_HEADERS
                                .forEach(headers::remove);

                        headers.set(
                                GatewayHeaders.GATEWAY_SECRET,
                                gatewaySecret);
                    })
                    .build();

            return chain.filter(
                    exchange.mutate()
                            .request(swaggerRequest)
                            .build());
        }

        // Public authentication endpoints
        // Remove client-supplied identity headers.
        if (rules.isPublic(method, path)) {

            ServerHttpRequest cleaned = request.mutate()
                    .headers(headers ->
                            GatewayHeaders.TRUSTED_HEADERS
                                    .forEach(headers::remove))
                    .build();

            return chain.filter(
                    exchange.mutate()
                            .request(cleaned)
                            .build());
        }

        // Validate Authorization header
        String authorization = request.getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)
                || authorization.length() <= BEARER_PREFIX.length()) {

            return writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Missing or malformed Authorization header "
                            + "(expected 'Bearer <token>')");
        }

        String token = authorization
                .substring(BEARER_PREFIX.length())
                .trim();

        JwtValidator.AuthenticatedUser user;

        try {
            user = jwtValidator.validate(token);

        } catch (ExpiredJwtException e) {

            return writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Token has expired");

        } catch (JwtValidator.MissingClaimsException e) {

            return writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    e.getMessage());

        } catch (JwtException | IllegalArgumentException e) {

            log.debug("Rejected invalid JWT: {}", e.getMessage());

            return writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Invalid token");
        }

        // Role-based authorization
        if (!rules.isAllowed(method, path, user.role())) {

            return writeError(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "Role " + user.role()
                            + " is not allowed to "
                            + method + " " + path);
        }

        // Forward trusted identity headers to downstream service
        ServerHttpRequest forwarded = request.mutate()
                .headers(headers -> {

                    GatewayHeaders.TRUSTED_HEADERS
                            .forEach(headers::remove);

                    headers.set(
                            GatewayHeaders.USER_ID,
                            String.valueOf(user.userId()));

                    headers.set(
                            GatewayHeaders.USER_NAME,
                            user.username());

                    headers.set(
                            GatewayHeaders.USER_ROLE,
                            user.role());

                    headers.set(
                            GatewayHeaders.GATEWAY_SECRET,
                            gatewaySecret);
                })
                .build();

        return chain.filter(
                exchange.mutate()
                        .request(forwarded)
                        .build());
    }

    /**
     * Swagger UI and OpenAPI documentation paths.
     */
    private static boolean isSwaggerPath(String path) {

        return path.equals("/swagger-ui.html")
                || path.equals("/swagger-ui")
                || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs")
                || path.startsWith("/v3/api-docs/")
                || path.startsWith("/webjars/");
    }

    /**
     * Runs before routing and load-balancing filters.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    /**
     * Reject path tricks that could bypass security rules.
     */
    private static boolean isSuspiciousPath(
            ServerHttpRequest request) {

        String raw = request.getURI().getRawPath();

        if (raw == null) {
            return false;
        }

        String lower = raw.toLowerCase(Locale.ROOT);

        return lower.contains("..")
                || lower.contains("//")
                || lower.contains("%2e")
                || lower.contains("%2f")
                || lower.contains("%5c")
                || lower.contains(";");
    }

    /**
     * Write JSON error response.
     */
    private Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            String message) {

        ServerHttpResponse response = exchange.getResponse();

        response.setStatusCode(status);
        response.getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders()
                    .set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        String body = "{"
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\""
                + escape(status.getReasonPhrase()) + "\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"path\":\""
                + escape(exchange.getRequest()
                        .getPath()
                        .value()) + "\""
                + "}";

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Escape JSON string values.
     */
    private static String escape(String value) {

        if (value == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(value.length());

        for (char c : value.toCharArray()) {

            switch (c) {

                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");

                default -> {
                    if (c < 0x20) {
                        sb.append(
                                String.format(
                                        "\\u%04x",
                                        (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }

        return sb.toString();
    }
}
