package com.evan.opensourcepulsebe.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueItem (
        Long id,
        Integer number,
        String title,
        @JsonProperty("html_url") String htmlUrl,
        String state,
        String body,
        Integer comments,
        @JsonProperty("repository_url") String repositoryUrl,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        List<GitHubLabelItem> labels
) { }
