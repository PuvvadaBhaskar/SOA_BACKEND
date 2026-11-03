package com.archivalia.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootTest
class EurekaServerApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
		assertThat(context).isNotNull();
	}

	@Test
	void registryDoesNotRegisterWithItself() {
		assertThat(environment.getProperty("eureka.client.register-with-eureka")).isEqualTo("false");
		assertThat(environment.getProperty("eureka.client.fetch-registry")).isEqualTo("false");
	}
}
