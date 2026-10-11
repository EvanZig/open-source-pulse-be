package com.evan.opensourcepulsebe.exception;

import java.time.Instant;

public class GitHubRateLimitException extends GitHubApiException {

    private final Instant retryAt;

    public GitHubRateLimitException(String message, Instant retryAt) {
        super(message);
        this.retryAt = retryAt;
    }

    public Instant getRetryAt() {
        return retryAt;
    }
}
