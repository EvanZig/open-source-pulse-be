package com.evan.opensourcepulsebe.issue;

import com.evan.opensourcepulsebe.common.dto.PagedResponse;
import com.evan.opensourcepulsebe.exception.ResourceNotFoundException;
import com.evan.opensourcepulsebe.issue.dto.IssueSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice test for {@link IssueController}.
 * Only MVC infrastructure (controllers, advice, Jackson, MockMvc) is started — no database, no Redis.
 * The service is replaced with a Mockito mock so we test HTTP concerns in isolation:
 * routing, query-param binding, pagination defaults, JSON shape and error mapping.
 */
@WebMvcTest(IssueController.class)
class IssueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueService issueService;

    private static final IssueSummaryResponse SAMPLE = new IssueSummaryResponse(
            1L, 100L, 7, "Fix memory leak",
            "https://github.com/facebook/react/issues/7", "open", "snippet",
            List.of("good first issue"), 3, Instant.parse("2026-10-01T00:00:00Z"),
            new IssueSummaryResponse.RepositorySummary(
                    10L, "facebook", "react", "https://github.com/facebook/react", 200_000L, "JavaScript"));

    @Test
    @DisplayName("GET /api/v1/issues returns 200 with the paged JSON body")
    void listIssues() throws Exception {
        when(issueService.searchIssues(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(SAMPLE), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Fix memory leak"))
                .andExpect(jsonPath("$.content[0].labels[0]").value("good first issue"))
                .andExpect(jsonPath("$.content[0].repository.owner").value("facebook"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/issues applies default pagination: size 20, newest first")
    void defaultPagination() throws Exception {
        when(issueService.searchIssues(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/issues")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(issueService).searchIssues(isNull(), isNull(), isNull(), captor.capture());

        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("githubCreatedAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("GET /api/v1/issues binds filter and paging query params")
    void bindsQueryParams() throws Exception {
        when(issueService.searchIssues(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/v1/issues")
                        .param("language", "Java")
                        .param("label", "good first issue")
                        .param("search", "docker")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(issueService).searchIssues(eq("Java"), eq("good first issue"), eq("docker"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("GET /api/v1/issues/{id} returns 200 with the issue")
    void getById() throws Exception {
        when(issueService.findById(1L)).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/v1/issues/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.repository.name").value("react"));
    }

    @Test
    @DisplayName("GET /api/v1/issues/{id} maps a missing issue to 404 NOT FOUND via GlobalExceptionHandler")
    void getByIdNotFound() throws Exception {
        when(issueService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Issue not found with id: 99"));

        mockMvc.perform(get("/api/v1/issues/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Issue not found with id: 99"))
                .andExpect(jsonPath("$.path").value("/api/v1/issues/99"));
    }
}

