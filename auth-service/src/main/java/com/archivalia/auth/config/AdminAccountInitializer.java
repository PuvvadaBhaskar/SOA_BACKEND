package com.archivalia.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.archivalia.auth.entity.User;
import com.archivalia.auth.repository.UserRepository;
import com.archivalia.auth.service.AuthService;

/**
 * Seeds the administrator account at startup from admin.username / admin.password / admin.email.
 * The password is BCrypt-hashed. An existing account is never modified or promoted.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;

    public AdminAccountInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.username:}") String username,
            @Value("${admin.password:}") String password,
            @Value("${admin.email:}") String email) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (username.isBlank() || password.isBlank() || email.isBlank()) {
            log.warn("admin.username / admin.password / admin.email not fully configured - no admin seeded");
            return;
        }

        if (password.length() < 8) {
            log.warn("admin.password is shorter than 8 characters; the admin would not be able to log in - no admin seeded");
            return;
        }

        if (userRepository.existsByUsername(username)) {
            userRepository.findByUsername(username)
                    .filter(existing -> !AuthService.ROLE_ADMIN.equals(existing.getRole()))
                    .ifPresent(existing -> log.warn(
                            "User '{}' already exists with role {} - it was NOT promoted to ADMIN",
                            username, existing.getRole()));
            return;
        }

        if (userRepository.existsByEmail(email.toLowerCase())) {
            log.warn("Email '{}' already belongs to another account - no admin seeded", email);
            return;
        }

        User admin = new User(username, email.toLowerCase(), passwordEncoder.encode(password), AuthService.ROLE_ADMIN);
        userRepository.save(admin);

        log.info("Seeded administrator account '{}'", username);
    }
}
