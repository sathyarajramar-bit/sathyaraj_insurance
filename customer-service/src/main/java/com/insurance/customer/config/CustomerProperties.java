package com.insurance.customer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Business thresholds ({@code customer.*}) kept out of code so underwriting can change them via config. */
@Getter
@Setter
@ConfigurationProperties(prefix = "customer")
public class CustomerProperties {

    private int minimumAgeYears = 18;

    private Vehicle vehicle = new Vehicle();

    @Getter
    @Setter
    public static class Vehicle {
        /** Vehicles older than this (by manufacturing year) are not insurable on the platform. */
        private int maxAgeYears = 20;
    }
}
