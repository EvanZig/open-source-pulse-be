package com.evan.opensourcepulsebe.issue;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class IssueSpecification {

    private IssueSpecification() {
    }

    public static Specification<Issue> withFilters(String language, String label, String search) {
        return Specification
                .where(isOpen())
                .and(hasLanguage(language))
                .and(hasLabel(label))
                .and(containsSearchText(search));
    }

    public static Specification<Issue> isOpen() {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("state")), "open");
    }

    public static Specification<Issue> hasLanguage(String language) {
        return (root, query, cb) -> {
            if (language == null || language.isBlank()) {
                return null;
            }
            return cb.equal(
                    cb.lower(root.join("repository").get("language")),
                    language.trim().toLowerCase());
        };
    }

    /**
     * Filters issues having the specified label in their PostgreSQL text[] array.
     * Uses PostgreSQL's array_position(labels, ?) function.
     */
    public static Specification<Issue> hasLabel(String label) {
        return (root, query, cb) -> {
            if (label == null || label.isBlank()) {
                return null;
            }
            Expression<Integer> pos = cb.function(
                    "array_position",
                    Integer.class,
                    root.get("labels"),
                    cb.literal(label.trim()));
            return cb.greaterThan(pos, 0);
        };
    }

    public static Specification<Issue> containsSearchText(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
            Predicate bodyMatch = cb.like(cb.lower(root.get("bodySnippet")), pattern);
            return cb.or(titleMatch, bodyMatch);
        };
    }
}
