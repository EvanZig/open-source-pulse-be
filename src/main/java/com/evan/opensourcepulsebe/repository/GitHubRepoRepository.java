package com.evan.opensourcepulsebe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitHubRepoRepository extends JpaRepository<GitHubRepository, Long> {

    Optional<GitHubRepository> findByGithubId(Long githubId);

    Optional<GitHubRepository> findByOwnerIgnoreCaseAndNameIgnoreCase(String owner, String name);
   
    boolean existsByGithubId(Long githubId);
}
