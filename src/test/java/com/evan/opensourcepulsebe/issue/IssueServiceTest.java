package com.evan.opensourcepulsebe.issue;

import com.evan.opensourcepulsebe.common.dto.PagedResponse;
import com.evan.opensourcepulsebe.exception.ResourceNotFoundException;
import com.evan.opensourcepulsebe.github.dto.GitHubIssueItem;
import com.evan.opensourcepulsebe.issue.dto.IssueSummaryResponse;
import com.evan.opensourcepulsebe.issue.mapper.IssueMapper;
import com.evan.opensourcepulsebe.repository.GitHubRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IssueService}.
 * No Spring context is started: collaborators are Mockito mocks, so each test runs in milliseconds.
 */
@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    private static final Instant SEEN_AT = Instant.parse("2026-10-12T12:00:00Z");

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private IssueMapper issueMapper;

    @InjectMocks
    private IssueService issueService;

    @Captor
    private ArgumentCaptor<List<Issue>> savedIssuesCaptor;

    // ── searchIssues ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("searchIssues")
    class SearchIssues {

        @Test
        @DisplayName("maps entities to DTOs and wraps them with pagination metadata")
        void mapsAndWrapsPage() {
            Pageable pageable = PageRequest.of(0, 20);
            Issue issue = issue(1L, 100L);
            IssueSummaryResponse dto = summary(1L, 100L);

            when(issueRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(issue), pageable, 1));
            when(issueMapper.toSummaryResponse(issue)).thenReturn(dto);

            PagedResponse<IssueSummaryResponse> result =
                    issueService.searchIssues("java", "good first issue", null, pageable);

            assertThat(result.content()).containsExactly(dto);
            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.totalPages()).isEqualTo(1);
            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(20);
        }

        @Test
        @DisplayName("returns an empty page when nothing matches")
        void emptyResult() {
            Pageable pageable = PageRequest.of(0, 20);
            when(issueRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            PagedResponse<IssueSummaryResponse> result =
                    issueService.searchIssues(null, null, "nothing-matches", pageable);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    // ── findById ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns the mapped DTO when the issue exists")
        void found() {
            Issue issue = issue(42L, 4200L);
            IssueSummaryResponse dto = summary(42L, 4200L);
            when(issueRepository.findById(42L)).thenReturn(Optional.of(issue));
            when(issueMapper.toSummaryResponse(issue)).thenReturn(dto);

            assertThat(issueService.findById(42L)).isEqualTo(dto);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the issue does not exist")
        void notFound() {
            when(issueRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> issueService.findById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ── upsertAll ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upsertAll")
    class UpsertAll {

        private final GitHubRepository repo = GitHubRepository.builder()
                .githubId(1L).owner("facebook").name("react")
                .htmlUrl("https://github.com/facebook/react").stars(200_000L).language("JavaScript")
                .build();

        private final Map<String, GitHubRepository> repoCache = Map.of("facebook/react", repo);

        @Test
        @DisplayName("inserts issues that are not yet in the database")
        void insertsNewIssues() {
            GitHubIssueItem dto = gitHubItem(500L, "facebook/react");
            Issue newEntity = issue(null, 500L);

            when(issueRepository.findAllByGithubIdIn(anyCollection())).thenReturn(List.of());
            when(issueMapper.toEntity(dto, repo)).thenReturn(newEntity);

            issueService.upsertAll(List.of(dto), repoCache, SEEN_AT);

            verify(issueRepository).saveAll(savedIssuesCaptor.capture());
            assertThat(savedIssuesCaptor.getValue()).containsExactly(newEntity);
            assertThat(newEntity.getLastSeenAt()).isEqualTo(SEEN_AT);
            verify(issueMapper, never()).updateEntityFromDto(any(), any());
        }

        @Test
        @DisplayName("updates issues that already exist instead of inserting duplicates")
        void updatesExistingIssues() {
            GitHubIssueItem dto = gitHubItem(500L, "facebook/react");
            Issue existing = issue(7L, 500L);

            when(issueRepository.findAllByGithubIdIn(anyCollection())).thenReturn(List.of(existing));

            issueService.upsertAll(List.of(dto), repoCache, SEEN_AT);

            verify(issueMapper).updateEntityFromDto(dto, existing);
            verify(issueMapper, never()).toEntity(any(), any());
            verify(issueRepository).saveAll(savedIssuesCaptor.capture());
            assertThat(savedIssuesCaptor.getValue()).containsExactly(existing);
        }

        @Test
        @DisplayName("skips issues whose repository could not be resolved")
        void skipsUnresolvedRepository() {
            GitHubIssueItem orphan = gitHubItem(600L, "unknown/repo");
            when(issueRepository.findAllByGithubIdIn(anyCollection())).thenReturn(List.of());

            issueService.upsertAll(List.of(orphan), repoCache, SEEN_AT);

            verify(issueRepository).saveAll(savedIssuesCaptor.capture());
            assertThat(savedIssuesCaptor.getValue()).isEmpty();
            verify(issueMapper, never()).toEntity(any(), any());
        }

        @Test
        @DisplayName("touches lastSeenAt for every incoming GitHub id")
        void touchesLastSeenAt() {
            GitHubIssueItem dto = gitHubItem(500L, "facebook/react");
            when(issueRepository.findAllByGithubIdIn(anyCollection())).thenReturn(List.of());
            when(issueMapper.toEntity(dto, repo)).thenReturn(issue(null, 500L));

            issueService.upsertAll(List.of(dto), repoCache, SEEN_AT);

            verify(issueRepository).touchLastSeenAt(java.util.Set.of(500L), SEEN_AT);
        }

        @Test
        @DisplayName("does not run the touch query for an empty batch")
        void emptyBatch() {
            when(issueRepository.findAllByGithubIdIn(anyCollection())).thenReturn(List.of());

            issueService.upsertAll(List.of(), repoCache, SEEN_AT);

            verify(issueRepository).saveAll(anyList());
            verify(issueRepository, never()).touchLastSeenAt(anyCollection(), any());
        }
    }

    // ── deleteStaleIssues ────────────────────────────────────────────────

    @Test
    @DisplayName("deleteStaleIssues returns the number of rows removed")
    void deleteStaleIssues() {
        when(issueRepository.deleteByLastSeenAtBefore(SEEN_AT)).thenReturn(3);

        assertThat(issueService.deleteStaleIssues(SEEN_AT)).isEqualTo(3);
    }

    // ── Test data builders ───────────────────────────────────────────────

    private static Issue issue(Long id, Long githubId) {
        return Issue.builder()
                .id(id)
                .githubId(githubId)
                .number(1)
                .title("Fix the thing")
                .htmlUrl("https://github.com/facebook/react/issues/1")
                .githubCreatedAt(SEEN_AT)
                .githubUpdatedAt(SEEN_AT)
                .build();
    }

    private static IssueSummaryResponse summary(Long id, Long githubId) {
        return new IssueSummaryResponse(id, githubId, 1, "Fix the thing",
                "https://github.com/facebook/react/issues/1", "open", null,
                List.of("good first issue"), 0, SEEN_AT, null);
    }

    private static GitHubIssueItem gitHubItem(Long githubId, String ownerAndName) {
        return new GitHubIssueItem(githubId, 1, "Fix the thing",
                "https://github.com/" + ownerAndName + "/issues/1", "open", "body", 2,
                "https://api.github.com/repos/" + ownerAndName,
                SEEN_AT, SEEN_AT, List.of());
    }
}

