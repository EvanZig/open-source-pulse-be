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
import java.time.temporal.ChronoUnit;
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

    @Value("${app.sync.stale-retention-days:7}")
    private int staleRetentionDays;

    private static final String SEARCH_QUERY = "is:issue is:open no:assignee stars:>=1000 comments:1..10";

    private static final int MAX_PAGES = 10;
    private static final int PER_PAGE = 100;
    private static final long PAGE_DELAY_MS = 10_000;
    private static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofMinutes(3);
    private static final int MAX_PAGE_RETRIES = 3;

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

            // 1. Resolve unique repositories once for the entire batch
            Map<String, GitHubRepository> repoCache = resolveRepositories(allIssues);

            // 2. Single batch SQL upsert + single Redis cache eviction
            issueService.upsertAll(allIssues, repoCache, syncStartedAt);

            // 3. Smart prune:
            // - Full sync: immediately prune closed/assigned issues not seen in this run (cutoff = syncStartedAt).
            // - Partial sync fallback: only prune issues older than the retention window (e.g. 7 days) to avoid data loss.
            if (result.complete()) {
                int pruned = issueService.deleteStaleIssues(syncStartedAt);
                log.info("Full sync complete. Processed {} issues, immediately pruned {} closed/assigned issues.",
                        allIssues.size(), pruned);
            } else {
                Instant fallbackCutoff = syncStartedAt.minus(staleRetentionDays, ChronoUnit.DAYS);
                int pruned = issueService.deleteStaleIssues(fallbackCutoff);
                log.warn("Partial sync complete. Processed {} issues before stopping; pruned {} issues older than {} days.",
                        allIssues.size(), pruned, staleRetentionDays);
            }

        } catch (Exception e) {
            log.error("Scheduled GitHub sync failed: {}", e.getMessage(), e);
        }
    }

    private FetchResult fetchAllPages() {
        Map<Long, GitHubIssueItem> allIssues = new LinkedHashMap<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            pauseBeforeNextPage(page);

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

            if (!appendPageItems(response, page, allIssues)) {
                break;
            }
        }

        return new FetchResult(new ArrayList<>(allIssues.values()), true);
    }

    private void pauseBeforeNextPage(int page) {
        if (page > 1) {
            log.info("Pausing {}s before fetching page {} to respect GitHub rate limits...",
                    PAGE_DELAY_MS / 1000, page);
            sleep(PAGE_DELAY_MS);
        }
    }

    /**
     * Appends the current page's issues into {@code allIssues}.
     *
     * @return {@code true} if there are more pages to fetch, {@code false} if pagination is complete.
     */
    private boolean appendPageItems(GitHubSearchResponse response, int page, Map<Long, GitHubIssueItem> allIssues) {
        List<GitHubIssueItem> pageItems = response.items();
        if (pageItems == null || pageItems.isEmpty()) {
            return false;
        }

        pageItems.forEach(item -> allIssues.putIfAbsent(item.id(), item));

        int reportedTotal = response.totalCount() != null ? response.totalCount() : 0;
        log.info("Fetched page {}: {} issues (unique so far: {}, GitHub total_count: {})",
                page, pageItems.size(), allIssues.size(), reportedTotal);

        int maxResults = MAX_PAGES * PER_PAGE;
        int fetchCap = reportedTotal > 0 ? Math.min(reportedTotal, maxResults) : maxResults;
        return (page * PER_PAGE) < fetchCap && allIssues.size() < fetchCap;
    }

    private GitHubSearchResponse fetchPage(int page) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return gitHubClient.searchOpenIssues(SEARCH_QUERY, "interactions", "desc", PER_PAGE, page);
            } catch (GitHubRateLimitException e) {
                if (attempt >= MAX_PAGE_RETRIES) {
                    log.warn("Exceeded max retries ({}) on page {}: {}", MAX_PAGE_RETRIES, page, e.getMessage());
                    throw e;
                }
                Duration wait = Duration.between(Instant.now(), e.getRetryAt()).plusSeconds(2);
                if (wait.isNegative() || wait.isZero()) {
                    wait = Duration.ofSeconds(60);
                }
                if (wait.compareTo(MAX_RATE_LIMIT_WAIT) > 0) {
                    log.warn("Rate limit wait duration ({}s) exceeds maximum allowed ({}s). Aborting page {}.",
                            wait.toSeconds(), MAX_RATE_LIMIT_WAIT.toSeconds(), page);
                    throw e;
                }
                log.info("Rate limited on page {} (attempt {}/{}). Waiting {}s before retrying...",
                        page, attempt, MAX_PAGE_RETRIES, wait.toSeconds());
                sleep(wait.toMillis());
            }
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
