package com.insurance.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicPathMatcherTest {

    private final PublicPathMatcher matcher = new PublicPathMatcher(List.of(
            "POST /api/auth/login",
            "GET /api/products/**",
            "/api/health-check"));

    @Test
    void methodSpecificRuleOnlyMatchesThatMethod() {
        assertThat(matcher.isPublic(HttpMethod.POST, "/api/auth/login")).isTrue();
        assertThat(matcher.isPublic(HttpMethod.GET, "/api/auth/login")).isFalse();
    }

    @Test
    void wildcardMatchesNestedPathsForConfiguredMethodOnly() {
        assertThat(matcher.isPublic(HttpMethod.GET, "/api/products")).isTrue();
        assertThat(matcher.isPublic(HttpMethod.GET, "/api/products/12/add-ons")).isTrue();
        assertThat(matcher.isPublic(HttpMethod.POST, "/api/products")).isFalse();
        assertThat(matcher.isPublic(HttpMethod.PUT, "/api/products/12")).isFalse();
    }

    @Test
    void ruleWithoutMethodMatchesAnyMethod() {
        assertThat(matcher.isPublic(HttpMethod.GET, "/api/health-check")).isTrue();
        assertThat(matcher.isPublic(HttpMethod.DELETE, "/api/health-check")).isTrue();
    }

    @Test
    void unlistedPathsAreProtected() {
        assertThat(matcher.isPublic(HttpMethod.GET, "/api/policies")).isFalse();
        assertThat(matcher.isPublic(HttpMethod.GET, "/api/productsXYZ")).isFalse();
    }
}
