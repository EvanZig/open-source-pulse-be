package com.evan.opensourcepulsebe.github;

import com.evan.opensourcepulsebe.exception.GitHubApiException;
import com.evan.opensourcepulsebe.exception.GitHubRateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Configuration
public class GitHubClientConfig {

    @Bean
    public GitHubClient gitHubClient(@Value("${github.api.token:}") String token) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "OpenSourcePulse-BE")

                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (req, res) -> {
                    String body = readBodySafely(res.getBody());
                    Instant retryAt = rateLimitRetryAt(res.getStatusCode(), res.getHeaders(), body);
                    if (retryAt != null) {
                        log.warn("GitHub rate limit hit ({}), retry allowed at {}. Response: {}",
                                res.getStatusCode(), retryAt, abbreviate(body));
                        throw new GitHubRateLimitException(
                                "GitHub rate limit exceeded, retry at " + retryAt + ": " + abbreviate(body), retryAt);
                    }
                    log.error("GitHub API client error: {} {} — {}",
                            res.getStatusCode(), res.getStatusText(), body);
                    throw new GitHubApiException(
                            "GitHub API returned " + res.getStatusCode() + ": " + abbreviate(body));
                })
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (req, res) -> {
                    String body = readBodySafely(res.getBody());
                    log.error("GitHub API server error: {} {} — {}",
                            res.getStatusCode(), res.getStatusText(), body);
                    throw new GitHubApiException(
                            "GitHub API returned " + res.getStatusCode() + ": " + abbreviate(body));
                });


        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
            log.info("GitHub client configured WITH authentication (token length={})", token.length());
        } else {
            log.warn("GitHub client configured WITHOUT authentication — rate limit is 10 req/min "
                    + "(github.api.token / GITHUB_API_TOKEN is blank or unset)");
        }

        RestClient restClient = builder.build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(GitHubClient.class);
    }

    private static Instant rateLimitRetryAt(HttpStatusCode status, HttpHeaders headers, String body) {
        if (status.value() != 403 && status.value() != 429) {
            return null;
        }

        // 1. Explicit Retry-After header (seconds)
        Long retryAfterSeconds = parseLongSafely(headers.getFirst("Retry-After"));
        if (retryAfterSeconds != null) {
            return Instant.now().plusSeconds(retryAfterSeconds);
        }

        // 2. Primary rate limit exhausted (x-ratelimit-remaining == 0)
        if ("0".equals(headers.getFirst("x-ratelimit-remaining"))) {
            Long resetEpochSeconds = parseLongSafely(headers.getFirst("x-ratelimit-reset"));
            if (resetEpochSeconds != null) {
                return Instant.ofEpochSecond(resetEpochSeconds);
            }
        }

        // 3. Secondary rate limit (HTTP 429 or HTTP 403 with secondary rate limit body)
        if (status.value() == 429 || isSecondaryRateLimit(body)) {
            // GitHub recommendation for secondary rate limits when no Retry-After is supplied: wait 60s
            return Instant.now().plusSeconds(60);
        }

        return null;
    }

    private static boolean isSecondaryRateLimit(String body) {
        return body != null && body.toLowerCase().contains("secondary rate limit");
    }

    private static Long parseLongSafely(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.debug("Ignoring unparseable rate-limit header value '{}'", value);
            return null;
        }
    }

    private static String readBodySafely(java.io.InputStream body) {
        if (body == null) {
            return "";
        }
        try {
            return new String(body.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(unreadable body: " + e.getMessage() + ")";
        }
    }

    private static String abbreviate(String body) {
        if (body == null || body.isBlank()) {
            return "(empty body)";
        }
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300) + "...";
    }
}
