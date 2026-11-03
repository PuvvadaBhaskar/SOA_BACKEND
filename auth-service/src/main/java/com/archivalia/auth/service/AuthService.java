package com.archivalia.auth.service;

import java.util.Date;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archivalia.auth.dto.AuthResponse;
import com.archivalia.auth.dto.LoginRequest;
import com.archivalia.auth.dto.RegisterRequest;
import com.archivalia.auth.dto.RegisterResponse;
import com.archivalia.auth.dto.TokenValidationResponse;
import com.archivalia.auth.entity.User;
import com.archivalia.auth.exception.ConflictException;
import com.archivalia.auth.exception.InvalidCredentialsException;
import com.archivalia.auth.exception.InvalidTokenException;
import com.archivalia.auth.repository.UserRepository;
import com.archivalia.auth.security.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

@Service
public class AuthService {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Registers a new account. The role is always USER, whatever the client sends. */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email '" + email + "' is already registered");
        }

        User user = new User();

        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        user.setRole(ROLE_USER);

        User saved = userRepository.save(user);

        return new RegisterResponse(saved.getId(), saved.getUsername(), saved.getEmail(),
                saved.getRole(), "User registered successfully");
    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                jwtService.getExpirationMs() / 1000,
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
    }

    /** Validates a raw "Bearer xxx" header value and returns the token's identity. */
    public TokenValidationResponse validate(String authorizationHeader) {

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Missing or malformed Authorization header (expected 'Bearer <token>')");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();

        try {
            Claims claims = jwtService.parseClaims(token);
            Object userId = claims.get(JwtService.CLAIM_USER_ID);
            Date expiration = claims.getExpiration();

            return new TokenValidationResponse(
                    true,
                    userId instanceof Number number ? number.longValue() : null,
                    claims.getSubject(),
                    claims.get(JwtService.CLAIM_ROLE, String.class),
                    claims.getIssuer(),
                    expiration != null ? expiration.toInstant().toString() : null);

        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid token");
        }
    }
}
