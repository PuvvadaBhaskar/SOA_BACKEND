package com.archivalia.fine.exception;

import org.springframework.http.HttpStatus;

/** Requested resource does not exist (404). */
public class ResourceNotFoundException extends ApiException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
