package com.evan.opensourcepulsebe.exception;

import java.time.Instant;

public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {
    public ApiErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, Instant.now());
    }
}
