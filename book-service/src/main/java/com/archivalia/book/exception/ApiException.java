package com.archivalia.book.exception;

import org.springframework.http.HttpStatus;

/** Base class for exceptions that map to a specific HTTP status. */
public abstract class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
