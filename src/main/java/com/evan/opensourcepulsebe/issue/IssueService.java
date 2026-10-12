package com.evan.opensourcepulsebe.issue;

import com.evan.opensourcepulsebe.common.dto.PagedResponse;
import com.evan.opensourcepulsebe.config.CacheNames;
import com.evan.opensourcepulsebe.exception.ResourceNotFoundException;
import com.evan.opensourcepulsebe.github.dto.GitHubIssueItem;
import com.evan.opensourcepulsebe.issue.dto.IssueSummaryResponse;
import com.evan.opensourcepulsebe.issue.mapper.IssueMapper;
import com.evan.opensourcepulsebe.repository.GitHubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final IssueMapper issueMapper;

    @Cacheable(cacheNames = CacheNames.ISSUE_SEARCH, keyGenerator = IssueSearchKeyGenerator.BEAN_NAME)
    @Transactional(readOnly = true)
    public PagedResponse<IssueSummaryResponse> searchIssues(String language, String label, String search, Pageable pageable) {
        Specification<Issue> spec = IssueSpecification.withFilters(language, label, search);
        Page<IssueSummaryResponse> page = issueRepository.findAll(spec, pageable)
                .map(issueMapper::toSummaryResponse);
        return PagedResponse.from(page);
    }

    @Cacheable(cacheNames = CacheNames.ISSUE_BY_ID, key = "#id")
    @Transactional(readOnly = true)
    public IssueSummaryResponse findById(Long id) {
        return issueRepository.findById(id)
                .map(issueMapper::toSummaryResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found with id: " + id));
    }

    @CacheEvict(cacheNames = {CacheNames.ISSUE_SEARCH, CacheNames.ISSUE_BY_ID}, allEntries = true)
    @Transactional
    public void upsertAll(List<GitHubIssueItem> items, Map<String, GitHubRepository> repoCache, Instant seenAt) {

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
            issue.setLastSeenAt(seenAt);
            toSave.add(issue);
        }

        issueRepository.saveAll(toSave);

        if (!incomingGithubIds.isEmpty()) {
            issueRepository.touchLastSeenAt(incomingGithubIds, seenAt);
        }

        int updatedCount = (int) toSave.stream()
                .filter(i -> existingMap.containsKey(i.getGithubId()))
                .count();

        log.info("Persisted {} issues ({} updated, {} new).",
                toSave.size(), updatedCount, toSave.size() - updatedCount);
    }

    @CacheEvict(cacheNames = {CacheNames.ISSUE_SEARCH, CacheNames.ISSUE_BY_ID}, allEntries = true)
    @Transactional
    public int deleteStaleIssues(Instant seenAt) {
        int removed = issueRepository.deleteByLastSeenAtBefore(seenAt);
        log.info("Pruned {} stale issues not seen since {}.", removed, seenAt);
        return removed;
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
