package com.archivalia.borrow.client;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.archivalia.borrow.exception.ApiException;
import com.archivalia.borrow.exception.BadRequestException;
import com.archivalia.borrow.exception.ConflictException;
import com.archivalia.borrow.exception.ResourceNotFoundException;
import com.archivalia.borrow.exception.ServiceUnavailableException;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/**
 * Maps a failed Feign call to this service's exceptions:
 * downstream 404 / 409 / 400 keep their meaning (business errors), while
 * timeouts, connection failures, 5xx and an open circuit become 503 Service Unavailable.
 */
public final class RemoteCallErrors {

    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private RemoteCallErrors() {
    }

    public static ApiException translate(String service, Throwable failure) {

        Throwable cause = unwrap(failure);

        if (cause instanceof ApiException apiException) {
            return apiException;
        }

        if (cause instanceof CallNotPermittedException) {
            return new ServiceUnavailableException(
                    service + " is temporarily unavailable (circuit breaker open), please retry later");
        }

        if (cause instanceof TimeoutException) {
            return new ServiceUnavailableException(service + " did not respond in time, please retry later");
        }

        if (cause instanceof FeignException feign) {
            int status = feign.status();
            String downstreamMessage = extractMessage(feign);

            switch (status) {
                case 404:
                    return new ResourceNotFoundException(orDefault(downstreamMessage, "Resource not found in " + service));
                case 409:
                    return new ConflictException(orDefault(downstreamMessage, service + " reported a conflict"));
                case 400:
                case 422:
                    return new BadRequestException(orDefault(downstreamMessage, service + " rejected the request"));
                case 401:
                case 403:
                    // Our own credentials were rejected: a configuration problem (GATEWAY_SECRET mismatch).
                    return new ServiceUnavailableException(service + " rejected the internal call (HTTP " + status
                            + ") - check that GATEWAY_SECRET is identical in all services");
                default:
                    return new ServiceUnavailableException(service + " is unavailable"
                            + (status > 0 ? " (HTTP " + status + ")" : "") + ", please retry later");
            }
        }

        return new ServiceUnavailableException(service + " is unavailable, please retry later");
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String extractMessage(FeignException exception) {
        try {
            String body = exception.contentUTF8();
            if (body == null || body.isBlank()) {
                return null;
            }
            Matcher matcher = MESSAGE.matcher(body);
            return matcher.find() ? matcher.group(1).replace("\\\"", "\"") : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
