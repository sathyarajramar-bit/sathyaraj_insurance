package com.insurance.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code security.public-paths}: endpoints that need no authentication in this service.
 * Entries are {@code "<pattern>"} (any HTTP method) or {@code "<METHOD> <pattern>"} (that method only),
 * the same format the API Gateway uses, e.g. {@code "GET /api/products/**"}.
 * Health, OpenAPI and Swagger UI are always public.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "security")
public class SecurityPublicPathsProperties {

    private List<String> publicPaths = new ArrayList<>();

    public record PublicPath(HttpMethod method, String pattern) {
    }

    public List<PublicPath> parsedPublicPaths() {
        List<PublicPath> parsed = new ArrayList<>();
        for (String entry : publicPaths) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(" +", 2);
            if (parts.length == 2) {
                parsed.add(new PublicPath(HttpMethod.valueOf(parts[0].toUpperCase()), parts[1].trim()));
            } else {
                parsed.add(new PublicPath(null, parts[0]));
            }
        }
        return parsed;
    }
}
