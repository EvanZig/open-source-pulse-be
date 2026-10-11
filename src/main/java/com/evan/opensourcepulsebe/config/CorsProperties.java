package com.evan.opensourcepulsebe.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
                @NotEmpty(message = "Allowed origins list cannot be empty") List<String> allowedOrigins) {
}
