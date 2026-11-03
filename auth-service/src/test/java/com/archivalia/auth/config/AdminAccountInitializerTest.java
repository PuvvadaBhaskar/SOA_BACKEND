package com.archivalia.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.archivalia.auth.entity.User;
import com.archivalia.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void seedsAdminWithBcryptPassword() {
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(userRepository.existsByEmail("admin@archivalia.local")).thenReturn(false);

        new AdminAccountInitializer(userRepository, encoder, "admin", "Admin@12345", "admin@archivalia.local").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(encoder.matches("Admin@12345", captor.getValue().getPassword())).isTrue();
    }

    @Test
    void doesNotTouchExistingAccount() {
        User existing = new User("admin", "admin@archivalia.local", "hash", "USER");
        when(userRepository.existsByUsername("admin")).thenReturn(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        new AdminAccountInitializer(userRepository, encoder, "admin", "Admin@12345", "admin@archivalia.local").run(null);

        verify(userRepository, never()).save(any());
        assertThat(existing.getRole()).isEqualTo("USER");
    }

    @Test
    void skipsWhenNotConfigured() {
        new AdminAccountInitializer(userRepository, encoder, "", "", "").run(null);

        verify(userRepository, never()).save(any());
    }
}
