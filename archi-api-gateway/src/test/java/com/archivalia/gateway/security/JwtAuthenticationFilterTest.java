package com.archivalia.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "TestOnlyJwtSecretForGatewayUnitTests-0123456789abcdef";
    private static final String ISSUER = "archivalia-auth-service";
    private static final String GATEWAY_SECRET = "test-gateway-secret";

    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtAuthenticationFilter filter;
    private AtomicReference<ServerWebExchange> forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                new JwtValidator(SECRET, ISSUER), new RouteAuthorizationRules(), GATEWAY_SECRET);
        forwarded = new AtomicReference<>();
        chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    // ---------- public endpoints ----------

    @Test
    void loginAndRegisterPassWithoutToken() {
        MockServerWebExchange login = MockServerWebExchange.from(MockServerHttpRequest.post("/auth/login"));
        MockServerWebExchange register = MockServerWebExchange.from(MockServerHttpRequest.post("/auth/register"));

        filter.filter(login, chain).block();
        assertThat(forwarded.get()).isNotNull();

        forwarded.set(null);
        filter.filter(register, chain).block();
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void publicEndpointStillStripsSpoofedIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/auth/login")
                .header(GatewayHeaders.USER_ROLE, "ADMIN")
                .header(GatewayHeaders.GATEWAY_SECRET, "guess"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(GatewayHeaders.USER_ROLE)).isNull();
        assertThat(headers.getFirst(GatewayHeaders.GATEWAY_SECRET)).isNull();
    }

    // ---------- 401 ----------

    @Test
    void missingTokenReturns401Json() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/books"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"status\":401")
                .contains("\"error\":\"Unauthorized\"")
                .contains("\"path\":\"/books\"")
                .contains("\"timestamp\"")
                .contains("\"message\"");
    }

    @Test
    void malformedAuthorizationHeaderReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/books")
                .header(HttpHeaders.AUTHORIZATION, "Basic abc"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void tamperedTokenReturns401() {
        String token = token(7L, "alice", "USER", 60_000) + "x";

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/books"), token);
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Invalid token");
    }

    @Test
    void tokenSignedWithAnotherSecretReturns401() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "AnotherSecretThatIsAlsoLongEnough-0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject("mallory").issuer(ISSUER)
                .claim("role", "ADMIN").claim("userId", 1L)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey).compact();

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.delete("/books/1"), token);
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenReturns401WithExpiredMessage() {
        String token = token(7L, "alice", "USER", -60_000);

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/books"), token);
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Token has expired");
    }

    @Test
    void wrongIssuerReturns401() {
        String token = Jwts.builder().subject("alice").issuer("someone-else")
                .claim("role", "USER").claim("userId", 7L)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/books"), token);
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenWithoutUserIdClaimReturns401() {
        String token = Jwts.builder().subject("alice").issuer(ISSUER)
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/books"), token);
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- 403 ----------

    @Test
    void userCannotCreateBook() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/books"),
                token(7L, "alice", "USER", 60_000));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"status\":403").contains("\"error\":\"Forbidden\"");
    }

    @Test
    void userCannotListAllBorrowsOrDeleteFines() {
        String userToken = token(7L, "alice", "USER", 60_000);

        MockServerWebExchange listAll = exchange(MockServerHttpRequest.get("/borrows"), userToken);
        filter.filter(listAll, chain).block();
        assertThat(listAll.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        MockServerWebExchange deleteFine = exchange(MockServerHttpRequest.delete("/fines/3"), userToken);
        filter.filter(deleteFine, chain).block();
        assertThat(deleteFine.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---------- pass-through ----------

    @Test
    void validTokenIsForwardedWithIdentityHeadersAndSpoofedHeadersReplaced() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/books/5")
                .header(GatewayHeaders.USER_ID, "999")
                .header(GatewayHeaders.USER_ROLE, "ADMIN")
                .header(GatewayHeaders.GATEWAY_SECRET, "forged"),
                token(7L, "alice", "USER", 60_000));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        ServerHttpRequest request = forwarded.get().getRequest();
        assertThat(request.getHeaders().get(GatewayHeaders.USER_ID)).containsExactly("7");
        assertThat(request.getHeaders().get(GatewayHeaders.USER_NAME)).containsExactly("alice");
        assertThat(request.getHeaders().get(GatewayHeaders.USER_ROLE)).containsExactly("USER");
        assertThat(request.getHeaders().get(GatewayHeaders.GATEWAY_SECRET)).containsExactly(GATEWAY_SECRET);
    }

    @Test
    void adminMayCreateBooksAndUserMayBorrow() {
        MockServerWebExchange adminCreate = exchange(MockServerHttpRequest.post("/books"),
                token(1L, "admin", "ADMIN", 60_000));
        filter.filter(adminCreate, chain).block();
        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(GatewayHeaders.USER_ROLE)).isEqualTo("ADMIN");

        forwarded.set(null);
        MockServerWebExchange userBorrow = exchange(MockServerHttpRequest.post("/borrows"),
                token(7L, "alice", "USER", 60_000));
        filter.filter(userBorrow, chain).block();
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void encodedPathTricksAreRejected() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/books/..%2Fborrows"),
                token(7L, "alice", "USER", 60_000));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(forwarded.get()).isNull();
    }

    // ---------- helpers ----------

    private String token(Long userId, String username, String role, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuer(ISSUER)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now + ttlMillis))
                .signWith(key)
                .compact();
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> builder, String token) {
        return MockServerWebExchange.from(builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
