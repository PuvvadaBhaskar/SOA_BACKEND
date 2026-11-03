package com.archivalia.borrow.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import com.archivalia.borrow.dto.FineResponse;

/**
 * Load-balanced client for fine-service with circuit breaker / time limiter (see {@link BookClient}).
 */
@FeignClient(name = "FINE-SERVICE", fallbackFactory = FineClientFallbackFactory.class)
public interface FineClient {

    @PostMapping("/fines/calculate")
    FineResponse calculateFine(
            @RequestParam("borrowId") Long borrowId,
            @RequestParam("userId") Long userId,
            @RequestParam("bookId") Long bookId,
            @RequestParam("dueDate") String dueDate,
            @RequestParam("returnDate") String returnDate,
            @RequestHeader Map<String, String> headers
    );
}
