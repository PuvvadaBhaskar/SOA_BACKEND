package com.archivalia.book.exception;

import org.springframework.http.HttpStatus;

/** Authenticated caller is not allowed to touch this resource (403). */
public class ForbiddenException extends ApiException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
