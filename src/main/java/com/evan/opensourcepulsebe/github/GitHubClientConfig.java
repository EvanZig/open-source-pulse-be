package com.evan.opensourcepulsebe.github;

import com.evan.opensourcepulsebe.exception.GitHubApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

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
                    log.error("GitHub API client error: {} {}", res.getStatusCode(), res.getStatusText());
                    throw new GitHubApiException("GitHub API returned " + res.getStatusCode());
                })
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("GitHub API server error: {} {}", res.getStatusCode(), res.getStatusText());
                    throw new GitHubApiException("GitHub API returned " + res.getStatusCode());
                });


        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
            log.info("GitHub client configured WITH authentication");
        } else {
            log.warn("GitHub client configured WITHOUT authentication — rate limit is 10 req/min");
        }

        RestClient restClient = builder.build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(GitHubClient.class);
    }
}
