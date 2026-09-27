package com.evan.opensourcepulsebe.issue;

import com.evan.opensourcepulsebe.repository.GitHubRepository;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Setter
@Table(name = "issues",
uniqueConstraints = @UniqueConstraint(name = "uk_issues_github_id", columnNames = "github_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Issue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="github_id", nullable = false, updatable = false)
    private Long githubId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private GitHubRepository repository;

    @Column(nullable = false)
    private Integer number;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "html_url", nullable = false, length = 512)
    private String htmlUrl;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String state = "open";

    @Column(name = "body_snippet", columnDefinition = "TEXT")
    private String bodySnippet;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "labels", columnDefinition = "text[]")
    @Builder.Default
    private List<String> labels = new ArrayList<>();

    @Column(name = "comments_count", nullable = false)
    @Builder.Default
    private Integer commentsCount = 0;

    @Column(name = "github_created_at", nullable = false)
    private Instant githubCreatedAt;

    @Column(name = "github_updated_at", nullable = false)
    private Instant githubUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Issue issue = (Issue) o;
        return githubId != null && Objects.equals(githubId, issue.githubId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
