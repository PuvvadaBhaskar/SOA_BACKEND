package com.archivalia.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.archivalia.book.dto.BookRequest;
import com.archivalia.book.entity.Book;
import com.archivalia.book.exception.ConflictException;
import com.archivalia.book.repository.BookRepository;

/**
 * Exercises the real atomic UPDATE queries against H2.
 */
@SpringBootTest
class BookStockIntegrationTest {

    @Autowired
    private BookService service;

    @Autowired
    private BookRepository repository;

    @Test
    void borrowAndReturnAdjustStockAndNeverExceedBounds() {
        Book book = service.addBook(new BookRequest("Clean Code", "R. Martin", "978-0132350884", "SE", null, 1));

        assertThat(service.borrowBook(book.getId()).getAvailableCopies()).isZero();
        assertThatThrownBy(() -> service.borrowBook(book.getId())).isInstanceOf(ConflictException.class);

        assertThat(service.returnBook(book.getId()).getAvailableCopies()).isEqualTo(1);
        // returning again at full stock is a no-op, never more than totalCopies
        assertThat(service.returnBook(book.getId()).getAvailableCopies()).isEqualTo(1);
    }

    @Test
    void stockChangesBumpOptimisticLockVersion() {
        Book book = service.addBook(new BookRequest("Refactoring", "M. Fowler", "978-0134757599", "SE", null, 2));
        Long before = repository.findById(book.getId()).orElseThrow().getVersion();

        service.borrowBook(book.getId());

        assertThat(repository.findById(book.getId()).orElseThrow().getVersion()).isGreaterThan(before);
    }

    @Test
    void concurrentBorrowsNeverOversell() throws Exception {
        int copies = 3;
        int threads = 10;
        Book book = service.addBook(new BookRequest("DDD", "E. Evans", "978-0321125217", "SE", null, copies));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.borrowBook(book.getId());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }
        start.countDown();

        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdown();

        int available = repository.findById(book.getId()).orElseThrow().getAvailableCopies();
        assertThat(successes).isLessThanOrEqualTo(copies);
        assertThat(available).isGreaterThanOrEqualTo(0);
        assertThat(available).isEqualTo(copies - successes);
    }
}
