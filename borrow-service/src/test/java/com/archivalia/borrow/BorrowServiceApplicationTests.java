package com.archivalia.borrow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import com.archivalia.borrow.client.BookClient;
import com.archivalia.borrow.client.FineClient;

import feign.Retryer;

@SpringBootTest
class BorrowServiceApplicationTests {

	@Autowired
	private BookClient bookClient;

	@Autowired
	private FineClient fineClient;

	@Autowired
	private CircuitBreakerFactory<?, ?> circuitBreakerFactory;

	@Autowired
	private Retryer retryer;

	@Test
	void contextLoadsWithFeignClientsCircuitBreakerAndRetry() {
		assertThat(bookClient).isNotNull();
		assertThat(fineClient).isNotNull();
		assertThat(circuitBreakerFactory).isNotNull();
		assertThat(retryer).isNotNull();
	}

}
