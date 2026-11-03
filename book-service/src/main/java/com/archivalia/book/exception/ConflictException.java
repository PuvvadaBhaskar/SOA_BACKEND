package com.archivalia.book.exception;

import org.springframework.http.HttpStatus;

/** Request conflicts with existing state, e.g. duplicate username or email (409). */
public class ConflictException extends ApiException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
