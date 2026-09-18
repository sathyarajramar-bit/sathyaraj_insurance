package com.insurance.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Insurance product catalogue ({@code product_db}).
 *
 * <p>A product is an aggregate: the product row plus its coverages, add-ons, eligibility rules and
 * pricing configuration are created/updated together and cached together. Motor (CAR/BIKE) is the only
 * product type today; HEALTH/TRAVEL/LIFE reuse the same aggregate with different rule types.
 *
 * <p>This service knows <em>what</em> is sold and at which rates. It does not compute premiums for a
 * specific customer: quote-service reads the pricing configuration and runs the calculation engine.
 */
@SpringBootApplication
@EnableCaching
@ConfigurationPropertiesScan
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
