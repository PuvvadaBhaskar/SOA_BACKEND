package com.archivalia.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.archivalia.auth.repository.UserRepository;

@SpringBootTest
class AuthServiceApplicationTests {

	@Autowired
	private UserRepository userRepository;

	@Test
	void contextLoadsAndSeedsAdmin() {
		assertThat(userRepository.findByUsername("admin"))
				.hasValueSatisfying(admin -> {
					assertThat(admin.getRole()).isEqualTo("ADMIN");
					assertThat(admin.getPassword()).startsWith("$2"); // BCrypt hash, never plain text
				});
	}

}
