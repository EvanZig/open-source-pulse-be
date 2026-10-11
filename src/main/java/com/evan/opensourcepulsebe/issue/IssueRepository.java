package com.evan.opensourcepulsebe.issue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


public interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {

    Optional<Issue> findByGithubId(Long githubId);

    // Batch lookup: Fetches ALL issues matching a set of GitHub IDs in ONE query.
    // This is the key optimization — replaces 100 individual findByGithubId() calls.
    // Spring Data generates: SELECT * FROM issues WHERE github_id IN (?, ?, ?, ...)
    List<Issue> findAllByGithubIdIn(Collection<Long> githubIds);

    List<Issue> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    @Override
    @EntityGraph(attributePaths = {"repository"})
    Page<Issue> findAll(Specification<Issue> spec, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Issue i SET i.lastSeenAt = :seenAt WHERE i.githubId IN :githubIds")
    int touchLastSeenAt(@Param("githubIds") Collection<Long> githubIds, @Param("seenAt") Instant seenAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Issue i WHERE i.lastSeenAt < :seenAt")
    int deleteByLastSeenAtBefore(@Param("seenAt") Instant seenAt);
}

