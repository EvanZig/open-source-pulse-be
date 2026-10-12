package com.evan.opensourcepulsebe.exception;

/**
 * Thrown when a requested domain entity (e.g. Issue, Repository) is not found in the database.
 * Mapped to HTTP 404 NOT FOUND by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

