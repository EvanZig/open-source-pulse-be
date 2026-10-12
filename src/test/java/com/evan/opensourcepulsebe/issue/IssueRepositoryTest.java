package com.evan.opensourcepulsebe.issue;

import com.evan.opensourcepulsebe.repository.GitHubRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class IssueRepositoryTest {

    private static final String TEST_LANG_JAVA = "Java";
    private static final String TEST_LANG_PYTHON = "Python";
    private static final String TEST_LABEL = "good-first-issue";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private TestEntityManager entityManager;

    private GitHubRepository javaRepo;
    private GitHubRepository pythonRepo;

    @BeforeEach
    void setUp() {
        javaRepo = persistRepository(1L, "test-org-1", "test-repo-java", TEST_LANG_JAVA);
        pythonRepo = persistRepository(2L, "test-org-2", "test-repo-python", TEST_LANG_PYTHON);
    }

    @Nested
    @DisplayName("Specification Filters (PostgreSQL Criteria Queries)")
    class SpecificationFilterTests {

        @Test
        @DisplayName("hasLabel() queries PostgreSQL text[] array using array_position()")
        void hasLabel_shouldFilterUsingArrayPosition() {
            persistIssue(11L, javaRepo, 1, "Fix memory leak", "open",
                    List.of("bug", TEST_LABEL), "Memory leak in connection pool");
            persistIssue(12L, javaRepo, 2, "Docs typo", "open",
                    List.of("documentation"), "Typo in readme");

            // Clear Hibernate 1st-level cache so queries hit real PostgreSQL
            entityManager.clear();

            Page<Issue> result = issueRepository.findAll(
                    IssueSpecification.hasLabel(TEST_LABEL),
                    PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Fix memory leak");
            assertThat(result.getContent().get(0).getLabels()).contains(TEST_LABEL);

            // Searching for a non-existent label returns 0
            Page<Issue> emptyResult = issueRepository.findAll(
                    IssueSpecification.hasLabel("non-existent-label-xyz"),
                    PageRequest.of(0, 10));
            assertThat(emptyResult.getContent()).isEmpty();
        }

        @Test
        @DisplayName("hasLanguage() joins repository and matches case-insensitively")
        void hasLanguage_shouldJoinRepositoryAndMatchCaseInsensitively() {
            persistIssue(21L, javaRepo, 1, "Java Issue", "open", List.of(), "Details");
            persistIssue(22L, pythonRepo, 1, "Python Issue", "open", List.of(), "Details");

            entityManager.clear();

            // Case-insensitive query (lowercase "java" matches "Java")
            Page<Issue> result = issueRepository.findAll(
                    IssueSpecification.hasLanguage("java"),
                    PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Java Issue");
            assertThat(result.getContent().get(0).getRepository().getLanguage()).isEqualTo(TEST_LANG_JAVA);
        }

        @Test
        @DisplayName("isOpen() returns only open issues")
        void isOpen_shouldExcludeClosedIssues() {
            Issue openIssue = persistIssue(31L, javaRepo, 1, "Active bug", "open", List.of(), "Active");
            Issue closedIssue = persistIssue(32L, javaRepo, 2, "Resolved bug", "closed", List.of(), "Closed");

            entityManager.clear();

            // Query scoped to the test open issue
            Specification<Issue> openSpec = Specification.where(IssueSpecification.isOpen())
                    .and((root, query, cb) -> cb.equal(root.get("id"), openIssue.getId()));
            Page<Issue> openResult = issueRepository.findAll(openSpec, PageRequest.of(0, 10));
            assertThat(openResult.getContent()).hasSize(1);
            assertThat(openResult.getContent().get(0).getTitle()).isEqualTo("Active bug");

            // Query scoped to the test closed issue (should be excluded by isOpen)
            Specification<Issue> closedSpec = Specification.where(IssueSpecification.isOpen())
                    .and((root, query, cb) -> cb.equal(root.get("id"), closedIssue.getId()));
            Page<Issue> closedResult = issueRepository.findAll(closedSpec, PageRequest.of(0, 10));
            assertThat(closedResult.getContent()).isEmpty();
        }

        @Test
        @DisplayName("containsSearchText() matches case-insensitively in title and bodySnippet")
        void containsSearchText_shouldMatchInTitleOrBody() {
            persistIssue(41L, javaRepo, 1, "UniqueTitleNpeCrash", "open",
                    List.of(), "Standard log output");
            persistIssue(42L, javaRepo, 2, "Feature Request", "open",
                    List.of(), "Please add UniqueBodyIngressConfig to support mesh");

            entityManager.clear();

            // Match by title
            Page<Issue> titleMatches = issueRepository.findAll(
                    IssueSpecification.containsSearchText("npecrash"),
                    PageRequest.of(0, 10));
            assertThat(titleMatches.getContent()).extracting(Issue::getTitle)
                    .containsExactly("UniqueTitleNpeCrash");

            // Match by bodySnippet
            Page<Issue> bodyMatches = issueRepository.findAll(
                    IssueSpecification.containsSearchText("ingressconfig"),
                    PageRequest.of(0, 10));
            assertThat(bodyMatches.getContent()).extracting(Issue::getTitle)
                    .containsExactly("Feature Request");
        }

        @Test
        @DisplayName("withFilters() composes all specifications together")
        void withFilters_shouldCombineAllPredicates() {
            // Target match: open, Java, label good-first-issue, search "atomiccache"
            persistIssue(51L, javaRepo, 1, "Implement atomiccache layer", "open",
                    List.of(TEST_LABEL, "enhancement"), "Add cache layer");

            // Non-matches:
            persistIssue(52L, javaRepo, 2, "Implement atomiccache layer", "closed", // closed!
                    List.of(TEST_LABEL), "Add cache layer");
            persistIssue(53L, pythonRepo, 3, "Implement atomiccache layer", "open", // Python, not Java!
                    List.of(TEST_LABEL), "Add cache layer");
            persistIssue(54L, javaRepo, 4, "Implement atomiccache layer", "open", // missing label!
                    List.of("help wanted"), "Add cache layer");
            persistIssue(55L, javaRepo, 5, "Other task atomiccache", "open", // missing label!
                    List.of("bug"), "Nothing here");

            entityManager.clear();

            Page<Issue> result = issueRepository.findAll(
                    IssueSpecification.withFilters(TEST_LANG_JAVA, TEST_LABEL, "atomiccache"),
                    PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Implement atomiccache layer");
            assertThat(result.getContent().get(0).getGithubId()).isEqualTo(51L);
        }
    }

    @Nested
    @DisplayName("Custom Queries & Batch Operations")
    class CustomQueryTests {

        @Test
        @DisplayName("findAllByGithubIdIn() batch fetches existing issues")
        void findAllByGithubIdIn_shouldReturnMatchingIssues() {
            persistIssue(61L, javaRepo, 1, "Issue 1", "open", List.of(), "Body 1");
            persistIssue(62L, javaRepo, 2, "Issue 2", "open", List.of(), "Body 2");
            persistIssue(63L, javaRepo, 3, "Issue 3", "open", List.of(), "Body 3");

            entityManager.clear();

            List<Issue> found = issueRepository.findAllByGithubIdIn(List.of(61L, 63L, 999L));

            assertThat(found).extracting(Issue::getGithubId)
                    .containsExactlyInAnyOrder(61L, 63L);
        }

        @Test
        @DisplayName("touchLastSeenAt() updates timestamps for specified GitHub IDs in bulk")
        void touchLastSeenAt_shouldUpdateTimestamp() {
            persistIssue(71L, javaRepo, 1, "Issue 1", "open", List.of(), "Body 1");
            persistIssue(72L, javaRepo, 2, "Issue 2", "open", List.of(), "Body 2");

            entityManager.clear();

            Instant newTimestamp = Instant.now().plus(1, ChronoUnit.HOURS);
            int updatedCount = issueRepository.touchLastSeenAt(List.of(71L), newTimestamp);

            assertThat(updatedCount).isEqualTo(1);

            entityManager.clear();
            Issue reloaded1 = issueRepository.findByGithubId(71L).orElseThrow();
            Issue reloaded2 = issueRepository.findByGithubId(72L).orElseThrow();

            assertThat(reloaded1.getLastSeenAt()).isCloseTo(newTimestamp, within(1, ChronoUnit.SECONDS));
            assertThat(reloaded2.getLastSeenAt()).isBefore(newTimestamp);
        }

        @Test
        @DisplayName("deleteByLastSeenAtBefore() removes stale issues older than threshold")
        void deleteByLastSeenAtBefore_shouldDeleteStaleIssues() {
            Instant staleTime = Instant.now().minus(2, ChronoUnit.DAYS);
            Instant freshTime = Instant.now();

            persistIssueWithLastSeen(81L, javaRepo, 1, "Stale Issue", staleTime);
            persistIssueWithLastSeen(82L, javaRepo, 2, "Fresh Issue", freshTime);

            entityManager.clear();

            Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
            int deletedCount = issueRepository.deleteByLastSeenAtBefore(cutoff);

            // Isolated Testcontainers DB: only the one stale fixture should be deleted
            assertThat(deletedCount).isEqualTo(1);

            entityManager.clear();
            assertThat(issueRepository.findByGithubId(81L)).isEmpty();
            assertThat(issueRepository.findByGithubId(82L)).isPresent();
        }
    }

    // --- Test Data Builders ---

    private GitHubRepository persistRepository(Long githubId, String owner, String name, String language) {
        GitHubRepository repo = GitHubRepository.builder()
                .githubId(githubId)
                .owner(owner)
                .name(name)
                .htmlUrl("https://github.com/" + owner + "/" + name)
                .description("Test repository for " + name)
                .stars(1000L)
                .language(language)
                .build();
        return entityManager.persistAndFlush(repo);
    }

    private Issue persistIssue(Long githubId, GitHubRepository repo, int number,
                               String title, String state, List<String> labels, String bodySnippet) {
        return persistIssueWithLastSeen(githubId, repo, number, title, state, labels, bodySnippet, Instant.now());
    }

    private Issue persistIssueWithLastSeen(Long githubId, GitHubRepository repo, int number,
                                           String title, Instant lastSeenAt) {
        return persistIssueWithLastSeen(githubId, repo, number, title, "open", List.of(), "Body", lastSeenAt);
    }

    private Issue persistIssueWithLastSeen(Long githubId, GitHubRepository repo, int number,
                                           String title, String state, List<String> labels,
                                           String bodySnippet, Instant lastSeenAt) {
        Instant now = Instant.now();
        Issue issue = Issue.builder()
                .githubId(githubId)
                .repository(repo)
                .number(number)
                .title(title)
                .htmlUrl(repo.getHtmlUrl() + "/issues/" + number)
                .state(state)
                .bodySnippet(bodySnippet)
                .labels(labels)
                .commentsCount(0)
                .githubCreatedAt(now)
                .githubUpdatedAt(now)
                .lastSeenAt(lastSeenAt)
                .build();
        return entityManager.persistAndFlush(issue);
    }
}
