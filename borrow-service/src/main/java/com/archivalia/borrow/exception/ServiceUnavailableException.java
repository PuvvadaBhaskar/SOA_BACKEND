package com.archivalia.borrow.exception;

import org.springframework.http.HttpStatus;

/** A downstream service is down, timing out or its circuit breaker is open (503). */
public class ServiceUnavailableException extends ApiException {

    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
