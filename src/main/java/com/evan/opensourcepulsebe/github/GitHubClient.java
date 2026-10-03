package com.evan.opensourcepulsebe.github;

import com.evan.opensourcepulsebe.github.dto.GitHubIssueItem;
import com.evan.opensourcepulsebe.github.dto.GitHubRepoItem;
import com.evan.opensourcepulsebe.github.dto.GitHubSearchResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * Declarative HTTP client for the GitHub REST API.
 *
 * Spring generates the implementation at startup via
 * {@link GitHubClientConfig}. Each method maps 1:1 to a
 * GitHub endpoint — no manual URI building needed.
 */
@HttpExchange
public interface GitHubClient {

    // ── Scheduled bulk fetch ─────────────────────────────────────────────

    /**
     * Searches for beginner-friendly open issues on popular repos.
     * Called by the scheduled sync job every 6 hours.
     * Uses the Search API (30 req/min limit).
     */
    @GetExchange("/search/issues")
    GitHubSearchResponse searchOpenIssues(
            @RequestParam("q") String query,
            @RequestParam("sort") String sort,
            @RequestParam("order") String order,
            @RequestParam("per_page") int perPage,
            @RequestParam("page") int page
    );

    // ── User-submitted repo lookup ───────────────────────────────────────

    /**
     * Fetches a single repository by owner and name.
     * Used when a user submits a GitHub URL like github.com/owner/repo.
     * Uses the Core API (5000 req/hour limit).
     */
    @GetExchange("/repos/{owner}/{repo}")
    GitHubRepoItem fetchRepository(
            @PathVariable String owner,
            @PathVariable String repo
    );

    /**
     * Fetches open issues for a specific repository.
     * Used when a user adds a repo and wants to browse/pick issues to track.
     * Uses the Core API (5000 req/hour limit), much faster and safer than Search API.
     */
    @GetExchange("/repos/{owner}/{repo}/issues")
    List<GitHubIssueItem> fetchRepoIssues(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam("state") String state,
            @RequestParam("sort") String sort,
            @RequestParam("direction") String direction,
            @RequestParam("per_page") int perPage,
            @RequestParam("page") int page
    );
}
