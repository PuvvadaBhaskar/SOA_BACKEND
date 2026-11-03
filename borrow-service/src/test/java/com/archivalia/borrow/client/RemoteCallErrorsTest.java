package com.archivalia.borrow.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.archivalia.borrow.exception.ApiException;
import com.archivalia.borrow.exception.ConflictException;
import com.archivalia.borrow.exception.ResourceNotFoundException;
import com.archivalia.borrow.exception.ServiceUnavailableException;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

class RemoteCallErrorsTest {

    @Test
    void downstream404KeepsMessage() {
        FeignException notFound = feign(404, "{\"status\":404,\"message\":\"Book not found with id 9\"}");

        ApiException mapped = RemoteCallErrors.translate("book-service", notFound);

        assertThat(mapped).isInstanceOf(ResourceNotFoundException.class);
        assertThat(mapped.getMessage()).isEqualTo("Book not found with id 9");
    }

    @Test
    void downstream409IsConflict() {
        assertThat(RemoteCallErrors.translate("book-service", feign(409, "{}")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void downstream5xxIs503() {
        ApiException mapped = RemoteCallErrors.translate("book-service", feign(500, ""));

        assertThat(mapped).isInstanceOf(ServiceUnavailableException.class);
        assertThat(mapped.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void connectionFailureIs503() {
        assertThat(RemoteCallErrors.translate("book-service", feign(-1, null)))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void openCircuitIs503() {
        CallNotPermittedException open =
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("book"));

        ApiException mapped = RemoteCallErrors.translate("book-service", open);

        assertThat(mapped).isInstanceOf(ServiceUnavailableException.class);
        assertThat(mapped.getMessage()).contains("circuit breaker open");
    }

    @Test
    void timeoutIs503EvenWhenWrapped() {
        ApiException mapped = RemoteCallErrors.translate("fine-service",
                new ExecutionException(new TimeoutException("slow")));

        assertThat(mapped).isInstanceOf(ServiceUnavailableException.class);
        assertThat(mapped.getMessage()).contains("did not respond in time");
    }

    @Test
    void secretMismatchIsReportedAs503() {
        assertThat(RemoteCallErrors.translate("book-service", feign(401, "")).getMessage())
                .contains("GATEWAY_SECRET");
    }

    private static FeignException feign(int status, String body) {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(status);
        when(exception.contentUTF8()).thenReturn(body);
        return exception;
    }
}
