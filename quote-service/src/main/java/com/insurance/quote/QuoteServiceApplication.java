package com.insurance.quote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns a customer + vehicle + product + add-on selection into a priced, time-limited quote.
 *
 * <p>Reads (synchronously, via OpenFeign) the product catalogue and pricing from product-service and the
 * customer's vehicle from customer-service, runs the premium engine, and stores a snapshot of everything
 * that influenced the price so the quote stays reproducible even if product or vehicle change later.
 * A scheduler expires quotes whose validity window has passed.
 */
@SpringBootApplication
@EnableFeignClients
@EnableCaching
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
public class QuoteServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuoteServiceApplication.class, args);
    }
}
