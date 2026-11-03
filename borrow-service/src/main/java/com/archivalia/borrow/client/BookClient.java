package com.archivalia.borrow.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import com.archivalia.borrow.dto.BookResponse;

/**
 * Load-balanced (lb via Eureka + Spring Cloud LoadBalancer) client for book-service, wrapped in a
 * Resilience4j circuit breaker + time limiter; failures go to {@link BookClientFallbackFactory}.
 * <p>
 * The caller's gateway headers (X-User-*, X-Gateway-Secret) are passed explicitly as a header map
 * because the time limiter runs the call on another thread, where the incoming request is not visible.
 */
@FeignClient(name = "BOOK-SERVICE", fallbackFactory = BookClientFallbackFactory.class)
public interface BookClient {

    @GetMapping("/books/{id}")
    BookResponse getBook(@PathVariable("id") Long id, @RequestHeader Map<String, String> headers);

    @PutMapping("/books/{id}/borrow")
    BookResponse borrowBook(@PathVariable("id") Long id, @RequestHeader Map<String, String> headers);

    @PutMapping("/books/{id}/return")
    BookResponse returnBook(@PathVariable("id") Long id, @RequestHeader Map<String, String> headers);
}
