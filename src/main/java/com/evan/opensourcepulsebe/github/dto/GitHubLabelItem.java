package com.evan.opensourcepulsebe.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubLabelItem(
        Long id,
        String name,
        String color
) {}
