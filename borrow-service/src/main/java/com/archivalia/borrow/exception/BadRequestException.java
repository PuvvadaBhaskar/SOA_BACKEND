package com.archivalia.borrow.exception;

import org.springframework.http.HttpStatus;

/** Request is syntactically valid but semantically wrong (400). */
public class BadRequestException extends ApiException {

    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
