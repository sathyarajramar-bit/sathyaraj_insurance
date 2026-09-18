package com.insurance.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Identity provider of the platform: registration, login, JWT issuing, refresh-token rotation and roles.
 *
 * <p>Owns {@code auth_db} (users, user_roles, refresh_tokens). Customer profile data lives in
 * customer-service; on registration this service creates the profile there through OpenFeign.
 */
@SpringBootApplication
@EnableFeignClients
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
