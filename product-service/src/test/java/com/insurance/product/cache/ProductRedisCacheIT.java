package com.insurance.product.cache;

import com.insurance.product.dto.ProductResponse;
import com.insurance.product.service.ProductService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Redis path end to end: typed JSON serialisation, key naming and TTL on a real Redis.
 * Runs only where Docker is available; the in-memory variant (ProductCachingIT) always runs.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cache.type=redis",
        "spring.autoconfigure.exclude=",
        "product.cache.product-ttl=30s"})
@ActiveProfiles("test")
class ProductRedisCacheIT {

    @Container
    static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired private ProductService productService;
    @Autowired private StringRedisTemplate redis;

    @Test
    void productIsStoredAsReadableJsonWithTtl() {
        ProductResponse first = productService.getById(1L);   // seeded MOTOR-CAR-COMP
        ProductResponse second = productService.getById(1L);

        assertThat(second).isEqualTo(first);
        String json = redis.opsForValue().get("products::1");
        assertThat(json).contains("\"code\":\"MOTOR-CAR-COMP\"").doesNotContain("@class");
        assertThat(redis.getExpire("products::1")).isBetween(1L, 30L);
    }
}
