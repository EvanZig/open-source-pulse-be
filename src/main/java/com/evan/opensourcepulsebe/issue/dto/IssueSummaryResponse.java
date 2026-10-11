package com.evan.opensourcepulsebe.issue.dto;

import java.time.Instant;
import java.util.List;

public record IssueSummaryResponse(
        Long id,
        Long githubId,
        Integer number,
        String title,
        String htmlUrl,
        String state,
        String bodySnippet,
        List<String> labels,
        Integer commentsCount,
        Instant githubCreatedAt,
        RepositorySummary repository) {
    public record RepositorySummary(
            Long id,
            String owner,
            String name,
            String htmlUrl,
            Long stars,
            String language) {
    }
}
