package com.archivalia.auth.exception;

import org.springframework.http.HttpStatus;

/** Wrong username or password (401). */
public class InvalidCredentialsException extends ApiException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
