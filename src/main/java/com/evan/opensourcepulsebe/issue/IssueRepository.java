package com.evan.opensourcepulsebe.issue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for Issue entity.
 */
public interface IssueRepository extends JpaRepository<Issue, Long> {

    Optional<Issue> findByGithubId(Long githubId);

    // Batch lookup: Fetches ALL issues matching a set of GitHub IDs in ONE query.
    // This is the key optimization — replaces 100 individual findByGithubId() calls.
    // Spring Data generates: SELECT * FROM issues WHERE github_id IN (?, ?, ?, ...)
    List<Issue> findAllByGithubIdIn(Collection<Long> githubIds);

    // Fetch all tracked issues for a specific repository
    List<Issue> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);
}
