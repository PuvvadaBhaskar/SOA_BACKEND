package com.archivalia.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.archivalia.gateway.security.JwtAuthenticationFilter;

@SpringBootTest
class ArchiApiGatewayApplicationTests {

	@Autowired
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@Test
	void contextLoadsWithGlobalJwtFilter() {
		assertThat(jwtAuthenticationFilter).isNotNull();
	}

}
