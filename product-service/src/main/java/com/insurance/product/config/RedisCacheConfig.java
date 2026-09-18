package com.insurance.product.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.common.dto.PageResponse;
import com.insurance.product.dto.PricingResponse;
import com.insurance.product.dto.ProductResponse;
import com.insurance.product.dto.ProductSummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache setup.
 *
 * <p>Values are stored as plain JSON with a <em>typed</em> serializer per cache (no {@code @class}
 * metadata, readable with redis-cli, safe against deserialisation gadgets). Keys are prefixed with the
 * cache name, e.g. {@code product-catalog::type=MOTOR|...}.
 *
 * <p>Interview notes:
 * <ul>
 *   <li><b>Why Redis and not the in-process cache?</b> Several instances of product-service must agree:
 *       when an admin updates a product on instance A, instance B must not keep serving the old one.
 *       A shared cache with explicit eviction gives that; TTL is the fallback.</li>
 *   <li><b>Cache outage:</b> {@link #errorHandler()} logs and continues (cache-aside degrades to
 *       "always miss"), so a Redis restart slows the catalogue down instead of taking it down.</li>
 *   <li><b>Alternatives:</b> Caffeine (local, fastest, per instance), Hibernate 2nd-level cache
 *       (entity granularity, harder to reason about), CDN for the public catalogue.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RedisCacheConfig implements CachingConfigurer {

    @Bean
    RedisCacheManagerBuilderCustomizer productCacheCustomizer(ProductCacheProperties properties) {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        JavaType catalogPageType = mapper.getTypeFactory()
                .constructParametricType(PageResponse.class, ProductSummaryResponse.class);

        return builder -> builder
                .withCacheConfiguration(CacheNames.PRODUCTS,
                        config(properties.getProductTtl(), new Jackson2JsonRedisSerializer<>(mapper, ProductResponse.class)))
                .withCacheConfiguration(CacheNames.PRODUCT_CATALOG,
                        config(properties.getCatalogTtl(), new Jackson2JsonRedisSerializer<>(mapper, catalogPageType)))
                .withCacheConfiguration(CacheNames.PRODUCT_PRICING,
                        config(properties.getPricingTtl(), new Jackson2JsonRedisSerializer<>(mapper, PricingResponse.class)));
    }

    private static RedisCacheConfiguration config(Duration ttl, RedisSerializer<?> valueSerializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
    }

    /** A cache failure must never fail a business request: log it and fall through to the database. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache GET failed for {}::{} - serving from database ({})", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed for {}::{} ({})", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.error("Cache EVICT failed for {}::{} - entry may be stale until TTL ({})", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.error("Cache CLEAR failed for {} - entries may be stale until TTL ({})", cache.getName(), e.getMessage());
            }
        };
    }
}
