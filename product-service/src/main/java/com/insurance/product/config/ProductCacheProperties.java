package com.insurance.product.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code product.cache.*}: TTL per cache. TTL is the safety net against stale data if an eviction is ever missed. */
@Getter
@Setter
@ConfigurationProperties(prefix = "product.cache")
public class ProductCacheProperties {

    private Duration productTtl = Duration.ofHours(1);
    private Duration catalogTtl = Duration.ofMinutes(10);
    private Duration pricingTtl = Duration.ofHours(1);
}
