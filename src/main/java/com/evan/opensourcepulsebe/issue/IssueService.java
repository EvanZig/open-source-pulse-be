package com.evan.opensourcepulsebe.issue;

import com.evan.opensourcepulsebe.github.dto.GitHubIssueItem;
import com.evan.opensourcepulsebe.issue.mapper.IssueMapper;
import com.evan.opensourcepulsebe.repository.GitHubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final IssueMapper issueMapper;

    @Transactional
    public void upsertAll(List<GitHubIssueItem> items, Map<String, GitHubRepository> repoCache) {

        Set<Long> incomingGithubIds = items.stream()
                .map(GitHubIssueItem::id)
                .collect(Collectors.toSet());

        Map<Long, Issue> existingMap = issueRepository
                .findAllByGithubIdIn(incomingGithubIds)
                .stream()
                .collect(Collectors.toMap(Issue::getGithubId, issue -> issue));

        List<Issue> toSave = new ArrayList<>(items.size());

        for (GitHubIssueItem dto : items) {
            String repoKey = extractRepoKey(dto.repositoryUrl());
            GitHubRepository repository = repoCache.get(repoKey);

            if (repository == null) {
                log.warn("Skipping issue {} — could not resolve repository: {}",
                        dto.id(), dto.repositoryUrl());
                continue;
            }

            Issue issue;
            if (existingMap.containsKey(dto.id())) {
                issue = existingMap.get(dto.id());
                issueMapper.updateEntityFromDto(dto, issue);
            } else {
                issue = issueMapper.toEntity(dto, repository);
            }

            toSave.add(issue);
        }

        issueRepository.saveAll(toSave);

        int updatedCount = (int) toSave.stream()
                .filter(i -> existingMap.containsKey(i.getGithubId()))
                .count();

        log.info("Persisted {} issues ({} updated, {} new).",
                toSave.size(), updatedCount, toSave.size() - updatedCount);
    }

    @Transactional(readOnly = true)
    public List<Issue> findByRepositoryId(Long repositoryId) {
        return issueRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId);
    }

    private String extractRepoKey(String repositoryUrl) {
        String[] parts = repositoryUrl.split("/");
        return parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }
}
