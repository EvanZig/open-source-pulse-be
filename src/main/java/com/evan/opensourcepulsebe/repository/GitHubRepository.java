package com.evan.opensourcepulsebe.repository;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(
        name = "repositories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_repositories_github_id", columnNames = "github_id"),
                @UniqueConstraint(name = "uk_repositories_owner_name", columnNames = {"owner", "name"})
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GitHubRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false, updatable = false)
    private Long githubId;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "html_url", nullable = false, length = 512)
    private String htmlUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Long stars;

    @Column(length = 50)
    private String language;

    @Builder
    private GitHubRepository(Long githubId, String owner, String name,
                             String htmlUrl, String description,
                             Long stars, String language) {
        this.githubId = githubId;
        this.owner = owner;
        this.name = name;
        this.htmlUrl = htmlUrl;
        this.description = description;
        this.stars = (stars != null) ? stars : 0L;
        this.language = language;
    }
}
