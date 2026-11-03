package com.archivalia.fine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.archivalia.fine.entity.Fine;
import com.archivalia.fine.service.FineService;

@SpringBootTest
class FineServiceApplicationTests {

	@Autowired
	private FineService fineService;

	@Test
	void contextLoadsAndCalculationIsIdempotentAgainstRealDatabase() {
		LocalDate due = LocalDate.of(2026, 1, 10);

		Fine first = fineService.calculateFine(500L, 7L, 3L, due, due.plusDays(2));
		Fine second = fineService.calculateFine(500L, 7L, 3L, due, due.plusDays(2));

		assertThat(first.getId()).isNotNull();
		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(first.getFineAmount()).isEqualTo(20.0);
	}

}
