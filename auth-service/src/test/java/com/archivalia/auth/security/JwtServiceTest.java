package com.archivalia.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = "UnitTestSecretKeyThatIsLongEnoughForHS256-0123456789";
    private static final String ISSUER = "archivalia-auth-service";

    private final JwtService jwtService = new JwtService(SECRET, 60_000, ISSUER);

    @Test
    void generatedTokenContainsSubjectRoleUserIdAndIssuer() {
        String token = jwtService.generateToken(42L, "alice", "USER");

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(42L);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.extractRole(token)).isEqualTo("USER");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void validTokenIsValid() {
        assertThat(jwtService.isValid(jwtService.generateToken(1L, "admin", "ADMIN"))).isTrue();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(SECRET, -1_000, ISSUER);
        String token = shortLived.generateToken(1L, "alice", "USER");

        assertThat(shortLived.isValid(token)).isFalse();
        assertThatThrownBy(() -> shortLived.parseClaims(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(1L, "alice", "USER");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService other = new JwtService("AnotherSecretKeyThatIsLongEnoughForHS256-987654321", 60_000, ISSUER);
        String foreignToken = other.generateToken(1L, "mallory", "ADMIN");

        assertThat(jwtService.isValid(foreignToken)).isFalse();
        assertThatThrownBy(() -> jwtService.parseClaims(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromDifferentIssuerIsRejected() {
        JwtService otherIssuer = new JwtService(SECRET, 60_000, "someone-else");
        String token = otherIssuer.generateToken(1L, "alice", "USER");

        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwtService.isValid("not-a-jwt")).isFalse();
        assertThat(jwtService.isValid("")).isFalse();
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtService("too-short", 60_000, ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
