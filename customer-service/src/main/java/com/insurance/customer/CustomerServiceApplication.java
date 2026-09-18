package com.insurance.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Owns customer profiles, KYC state and insured vehicles ({@code customer_db}).
 *
 * <p>Profiles are created by auth-service on registration (service-to-service call) and then
 * maintained by the customer. Later phases read vehicles from here when generating quotes.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
