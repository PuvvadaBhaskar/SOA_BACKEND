package com.archivalia.gateway.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Validates tokens issued by auth-service (same HMAC secret, same issuer, jjwt 0.12.x).
 */
@Component
public class JwtValidator {

    private final JwtParser parser;

    public JwtValidator(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer:archivalia-auth-service}") String issuer) {

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes (256 bits) long");
        }
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build();
    }

    /**
     * Verifies signature, expiry and issuer.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException when the token is expired
     * @throws JwtException for any other invalid token
     */
    public AuthenticatedUser validate(String token) {

        Claims claims = parser.parseSignedClaims(token).getPayload();

        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        Long userId = toLong(claims.get("userId"));

        if (username == null || username.isBlank()
                || role == null || role.isBlank()
                || userId == null) {
            throw new MissingClaimsException();
        }

        return new AuthenticatedUser(userId, username, role.toUpperCase());
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Identity extracted from a valid token. */
    public record AuthenticatedUser(Long userId, String username, String role) {
    }

    /** Signature is valid but the token lacks sub / role / userId. */
    public static class MissingClaimsException extends JwtException {
        private static final long serialVersionUID = 1L;

        public MissingClaimsException() {
            super("Token is missing required claims (sub, role, userId)");
        }
    }
}
