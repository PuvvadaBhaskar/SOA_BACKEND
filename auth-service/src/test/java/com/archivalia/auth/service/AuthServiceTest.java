package com.archivalia.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "UnitTestSecretKeyThatIsLongEnoughForHS256-0123456789";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService(SECRET, 60_000, "archivalia-auth-service");

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerAlwaysCreatesUserRoleAndHashesPassword() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        RegisterResponse response = authService.register(
                new RegisterRequest("alice", "Alice@Example.com", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getRole()).isEqualTo("USER");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void registerDuplicateUsernameThrowsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "a@b.com", "password123")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already taken");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerDuplicateEmailThrowsConflict() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("bob", "a@b.com", "password123")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void loginReturnsTokenWithUserIdAndRole() {
        User user = new User("alice", "alice@example.com", passwordEncoder.encode("password123"), "USER");
        user.setId(7L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getRole()).isEqualTo("USER");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(60L);
        assertThat(jwtService.extractUserId(response.getToken())).isEqualTo(7L);
    }

    @Test
    void loginWithWrongPasswordThrowsInvalidCredentials() {
        User user = new User("alice", "alice@example.com", passwordEncoder.encode("password123"), "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithUnknownUserThrowsInvalidCredentials() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "password123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void validateReturnsIdentityForGoodToken() {
        String token = jwtService.generateToken(3L, "carol", "ADMIN");

        TokenValidationResponse response = authService.validate("Bearer " + token);

        assertThat(response.valid()).isTrue();
        assertThat(response.userId()).isEqualTo(3L);
        assertThat(response.username()).isEqualTo("carol");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.issuer()).isEqualTo("archivalia-auth-service");
    }

    @Test
    void validateRejectsMissingOrBadToken() {
        assertThatThrownBy(() -> authService.validate(null)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> authService.validate("Bearer garbage")).isInstanceOf(InvalidTokenException.class);
    }
}
