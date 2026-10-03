package com.evan.opensourcepulsebe.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepoItem(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("html_url") String htmlUrl,
        String description,
        String language,
        @JsonProperty("stargazers_count") Long stargazersCount,
        @JsonProperty("open_issues_count") Integer openIssuesCount,
        GitHubOwnerItem owner
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubOwnerItem(
            String login
    ) {
    }
}
