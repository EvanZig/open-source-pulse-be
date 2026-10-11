package com.evan.opensourcepulsebe.issue;

import com.evan.opensourcepulsebe.issue.dto.IssueSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @GetMapping
    public ResponseEntity<Page<IssueSummaryResponse>> getIssues(
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "githubCreatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<IssueSummaryResponse> issues = issueService.searchIssues(language, label, search, pageable);
        return ResponseEntity.ok(issues);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IssueSummaryResponse> getIssueById(@PathVariable Long id) {
        IssueSummaryResponse issue = issueService.findById(id);
        return ResponseEntity.ok(issue);
    }
}
