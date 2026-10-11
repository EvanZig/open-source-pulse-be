package com.evan.opensourcepulsebe.github;

import com.evan.opensourcepulsebe.exception.GitHubApiException;
import com.evan.opensourcepulsebe.exception.GitHubRateLimitException;
import com.evan.opensourcepulsebe.github.dto.GitHubIssueItem;
import com.evan.opensourcepulsebe.github.dto.GitHubSearchResponse;
import com.evan.opensourcepulsebe.issue.IssueService;
import com.evan.opensourcepulsebe.repository.GitHubRepository;
import com.evan.opensourcepulsebe.repository.GitHubRepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubSyncService {

    private final GitHubClient gitHubClient;
    private final GitHubRepositoryService repositoryService;
    private final IssueService issueService;

    @Value("${github.api.token:}")
    private String githubApiToken;

    private static final String SEARCH_QUERY = "is:issue is:open no:assignee stars:>=1000 comments:1..10";

    private static final int MAX_PAGES = 10;
    private static final int PER_PAGE = 100;
    private static final long PAGE_DELAY_MS = 7_000;
    private static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofSeconds(90);

    @Scheduled(fixedRate = 6 * 60 * 60 * 1000, initialDelay = 10_000)
    public void syncBeginnerFriendlyIssues() {
        boolean authenticated = githubApiToken != null && !githubApiToken.isBlank();
        if (authenticated) {
            log.info("Starting scheduled GitHub sync WITH token (length={})", githubApiToken.length());
        } else {
            log.warn("Starting scheduled GitHub sync WITHOUT token — Search API limit is 10 req/min");
        }

        Instant syncStartedAt = Instant.now();

        try {
            FetchResult result = fetchAllPages();
            List<GitHubIssueItem> allIssues = result.issues();

            if (allIssues.isEmpty()) {
                log.info("No issues found from GitHub.");
                return;
            }

            Map<String, GitHubRepository> repoCache = resolveRepositories(allIssues);

            issueService.upsertAll(allIssues, repoCache, syncStartedAt);

            if (result.complete()) {
                issueService.deleteStaleIssues(syncStartedAt);
                log.info("Sync complete. Processed {} issues.", allIssues.size());
            } else {
                log.warn("Sync partially complete. Processed {} issues before GitHub stopped responding; "
                        + "skipped stale prune.", allIssues.size());
            }

        } catch (Exception e) {
            log.error("Scheduled GitHub sync failed: {}", e.getMessage(), e);
        }
    }

    private FetchResult fetchAllPages() {
        Map<Long, GitHubIssueItem> allIssues = new LinkedHashMap<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            if (page > 1) {
                sleep(PAGE_DELAY_MS);
            }

            GitHubSearchResponse response;
            try {
                response = fetchPage(page);
            } catch (GitHubApiException e) {
                if (allIssues.isEmpty()) {
                    throw e;
                }
                log.warn("Stopping at page {} ({}). Persisting {} issues already fetched.",
                        page, e.getMessage(), allIssues.size());
                return new FetchResult(new ArrayList<>(allIssues.values()), false);
            }

            List<GitHubIssueItem> pageItems = response.items();
            if (pageItems == null || pageItems.isEmpty()) {
                break;
            }

            pageItems.forEach(item -> allIssues.putIfAbsent(item.id(), item));

            int reportedTotal = response.totalCount() != null ? response.totalCount() : 0;
            log.info("Fetched page {}: {} issues (unique so far: {}, GitHub total_count: {})",
                    page, pageItems.size(), allIssues.size(), reportedTotal);

            int maxResults = MAX_PAGES * PER_PAGE;
            int fetchCap = reportedTotal > 0 ? Math.min(reportedTotal, maxResults) : maxResults;
            if (allIssues.size() >= fetchCap) {
                break;
            }
        }

        return new FetchResult(new ArrayList<>(allIssues.values()), true);
    }


    private GitHubSearchResponse fetchPage(int page) {
        try {
            return gitHubClient.searchOpenIssues(SEARCH_QUERY, "interactions", "desc", PER_PAGE, page);
        } catch (GitHubRateLimitException e) {
            Duration wait = Duration.between(Instant.now(), e.getRetryAt()).plusSeconds(1);
            if (wait.isNegative()) {
                wait = Duration.ofSeconds(1);
            }
            if (wait.compareTo(MAX_RATE_LIMIT_WAIT) > 0) {
                throw e;
            }
            log.info("Rate limited on page {}; waiting {}s before retrying.", page, wait.toSeconds());
            sleep(wait.toMillis());
            return gitHubClient.searchOpenIssues(SEARCH_QUERY, "interactions", "desc", PER_PAGE, page);
        }
    }

    private record FetchResult(List<GitHubIssueItem> issues, boolean complete) {
    }

    private Map<String, GitHubRepository> resolveRepositories(List<GitHubIssueItem> items) {
        Set<String> uniqueRepoKeys = items.stream()
                .map(item -> extractRepoKey(item.repositoryUrl()))
                .collect(Collectors.toSet());

        Map<String, GitHubRepository> cache = new HashMap<>();

        for (String repoKey : uniqueRepoKeys) {
            String[] parts = repoKey.split("/");
            String owner = parts[0];
            String name = parts[1];

            try {
                GitHubRepository repo = repositoryService.findOrFetchByOwnerAndName(owner, name);
                cache.put(repoKey, repo);
            } catch (Exception e) {
                log.warn("Failed to resolve repository {}: {}", repoKey, e.getMessage());
            }
        }

        log.info("Resolved {} unique repositories.", cache.size());
        return cache;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────

    private String extractRepoKey(String repositoryUrl) {
        String[] parts = repositoryUrl.split("/");
        return parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
