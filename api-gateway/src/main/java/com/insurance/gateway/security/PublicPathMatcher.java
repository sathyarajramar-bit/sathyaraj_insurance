package com.insurance.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * Decides whether a request may bypass JWT authentication.
 *
 * <p>Rules come from {@code gateway.security.public-paths} and use Spring's {@link PathPattern}
 * syntax ({@code /api/products/**}). A rule may be restricted to one HTTP method
 * ({@code "GET /api/products/**"}) so that browsing products is public while creating them is not.
 */
public class PublicPathMatcher {

    private record Rule(HttpMethod method, PathPattern pattern) {

        boolean matches(HttpMethod requestMethod, PathContainer path) {
            return (method == null || method.equals(requestMethod)) && pattern.matches(path);
        }
    }

    private final List<Rule> rules;

    public PublicPathMatcher(List<String> entries) {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.rules = entries.stream().map(String::trim).filter(e -> !e.isEmpty()).map(entry -> {
            String[] parts = entry.split("\\s+", 2);
            if (parts.length == 2) {
                return new Rule(HttpMethod.valueOf(parts[0].toUpperCase()), parser.parse(parts[1]));
            }
            return new Rule(null, parser.parse(parts[0]));
        }).toList();
    }

    public boolean isPublic(HttpMethod method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        return rules.stream().anyMatch(rule -> rule.matches(method, container));
    }
}
