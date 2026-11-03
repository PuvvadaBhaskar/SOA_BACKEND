package com.archivalia.borrow.client;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.archivalia.borrow.dto.FineResponse;

/** Circuit-breaker fallback for fine-service: throws the mapped exception (outages -> 503). */
@Component
public class FineClientFallbackFactory implements FallbackFactory<FineClient> {

    private static final Logger log = LoggerFactory.getLogger(FineClientFallbackFactory.class);

    @Override
    public FineClient create(Throwable cause) {

        log.warn("fine-service call failed: {}", cause.toString());

        return new FineClient() {

            @Override
            public FineResponse calculateFine(Long borrowId, Long userId, Long bookId, String dueDate,
                    String returnDate, Map<String, String> headers) {
                throw RemoteCallErrors.translate("fine-service", cause);
            }
        };
    }
}
