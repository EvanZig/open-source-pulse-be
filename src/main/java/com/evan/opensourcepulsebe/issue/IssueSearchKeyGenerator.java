package com.evan.opensourcepulsebe.issue;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Builds cache keys for {@link IssueService#searchIssues}. Filters are normalized the same way
 * {@link IssueSpecification} applies them, so equivalent queries share one cache entry.
 */
@Component(IssueSearchKeyGenerator.BEAN_NAME)
public class IssueSearchKeyGenerator implements KeyGenerator {

    public static final String BEAN_NAME = "issueSearchKeyGenerator";

    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length != 4 || !(params[3] instanceof Pageable pageable)) {
            throw new IllegalStateException("Unsupported method for " + BEAN_NAME + ": " + method);
        }

        return "lang=" + normalize((String) params[0], true)
                + "|label=" + normalize((String) params[1], false)
                + "|q=" + normalize((String) params[2], true)
                + "|page=" + pageable.getPageNumber()
                + "|size=" + pageable.getPageSize()
                + "|sort=" + pageable.getSort();
    }

    private static String normalize(String value, boolean caseInsensitive) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return caseInsensitive ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
    }
}
