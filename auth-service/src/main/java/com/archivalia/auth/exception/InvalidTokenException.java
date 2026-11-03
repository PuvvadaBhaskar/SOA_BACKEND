package com.archivalia.auth.exception;

import org.springframework.http.HttpStatus;

/** Missing, invalid or expired JWT (401). */
public class InvalidTokenException extends ApiException {

    private static final long serialVersionUID = 1L;

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
