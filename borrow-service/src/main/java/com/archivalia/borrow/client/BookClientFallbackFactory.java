package com.archivalia.borrow.client;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.archivalia.borrow.dto.BookResponse;

/**
 * Invoked by the circuit breaker when a book-service call fails, times out or the circuit is open.
 * It never fabricates data: it throws a mapped exception (404/409/400 pass through, outages -> 503).
 */
@Component
public class BookClientFallbackFactory implements FallbackFactory<BookClient> {

    private static final Logger log = LoggerFactory.getLogger(BookClientFallbackFactory.class);
    private static final String SERVICE = "book-service";

    @Override
    public BookClient create(Throwable cause) {

        log.warn("book-service call failed: {}", cause.toString());

        return new BookClient() {

            @Override
            public BookResponse getBook(Long id, Map<String, String> headers) {
                throw RemoteCallErrors.translate(SERVICE, cause);
            }

            @Override
            public BookResponse borrowBook(Long id, Map<String, String> headers) {
                throw RemoteCallErrors.translate(SERVICE, cause);
            }

            @Override
            public BookResponse returnBook(Long id, Map<String, String> headers) {
                throw RemoteCallErrors.translate(SERVICE, cause);
            }
        };
    }
}
