package com.evan.opensourcepulsebe.github;

import com.evan.opensourcepulsebe.github.dto.GitHubIssueItem;
import com.evan.opensourcepulsebe.github.dto.GitHubSearchResponse;
import com.evan.opensourcepulsebe.issue.IssueService;
import com.evan.opensourcepulsebe.repository.GitHubRepository;
import com.evan.opensourcepulsebe.repository.GitHubRepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the scheduled GitHub sync.
 * The domain services handle their own transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubSyncService {

    private final GitHubClient gitHubClient;
    private final GitHubRepositoryService repositoryService;
    private final IssueService issueService;

    private static final String SEARCH_QUERY = "is:issue is:open no:assignee stars:>=1000 comments:1..10";

    /**
     * Entry point — runs every 6 hours with a 10-second startup delay.
     */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000, initialDelay = 10_000)
    public void syncBeginnerFriendlyIssues() {
        log.info("Starting scheduled GitHub sync...");

        try {
            List<GitHubIssueItem> allIssues = fetchAllPages();

            if (allIssues.isEmpty()) {
                log.info("No issues found from GitHub.");
                return;
            }

            Map<String, GitHubRepository> repoCache = resolveRepositories(allIssues);

            issueService.upsertAll(allIssues, repoCache);

            log.info("Sync complete. Processed {} issues.", allIssues.size());

        } catch (Exception e) {
            log.error("Scheduled GitHub sync failed: {}", e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 1: Fetch from GitHub
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Results are de-duplicated by GitHub id: the search ranking shifts between
     * page
     * requests, so the same issue can appear on two pages, which would otherwise
     * produce two INSERTs and violate uk_issues_github_id.
     */
    private List<GitHubIssueItem> fetchAllPages() {
        Map<Long, GitHubIssueItem> allIssues = new LinkedHashMap<>();
        boolean hasMorePages = true;

        for (int page = 1; page <= 10 && hasMorePages; page++) {
            GitHubSearchResponse response = gitHubClient.searchOpenIssues(
                    SEARCH_QUERY, "interactions", "desc", 50, page);

            List<GitHubIssueItem> pageItems = response.items();

            if (pageItems == null || pageItems.isEmpty()) {
                hasMorePages = false;
            } else {
                pageItems.forEach(item -> allIssues.putIfAbsent(item.id(), item));
                log.info("Fetched page {}: {} issues (total so far: {})",
                        page, pageItems.size(), allIssues.size());

                hasMorePages = pageItems.size() == 50;

                if (hasMorePages) {
                    sleep(2000);
                }
            }
        }

        return new ArrayList<>(allIssues.values());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 2: Resolve repositories
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Extracts unique owner/name pairs from the issues and resolves each
     * to a GitHubRepository entity (from DB or GitHub API).
     */
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
