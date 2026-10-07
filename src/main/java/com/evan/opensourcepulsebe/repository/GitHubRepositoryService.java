package com.evan.opensourcepulsebe.repository;

import com.evan.opensourcepulsebe.github.GitHubClient;
import com.evan.opensourcepulsebe.github.dto.GitHubRepoItem;
import com.evan.opensourcepulsebe.repository.mapper.GitHubRepositoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubRepositoryService {

    private final GitHubRepoRepository repoRepository;
    private final GitHubClient gitHubClient;
    private final GitHubRepositoryMapper repositoryMapper;

    public GitHubRepository findOrFetchByOwnerAndName(String owner, String name) {
        return repoRepository.findByOwnerIgnoreCaseAndNameIgnoreCase(owner, name)
                .orElseGet(() -> {
                    log.info("Repository {}/{} not found in DB — fetching from GitHub.", owner, name);
                    GitHubRepoItem repoItem = gitHubClient.fetchRepository(owner, name);
                    // A renamed/transferred repo is redirected to its new owner/name, so it may already be stored.
                    return repoRepository.findByGithubId(repoItem.id())
                            .orElseGet(() -> repoRepository.save(repositoryMapper.toEntity(repoItem)));
                });
    }

    @Transactional(readOnly = true)
    public Optional<GitHubRepository> findByOwnerAndName(String owner, String name) {
        return repoRepository.findByOwnerIgnoreCaseAndNameIgnoreCase(owner, name);
    }

    @Transactional(readOnly = true)
    public Optional<GitHubRepository> findByGithubId(Long githubId) {
        return repoRepository.findByGithubId(githubId);
    }

    @Transactional
    public GitHubRepository save(GitHubRepository repository) {
        return repoRepository.save(repository);
    }
}
