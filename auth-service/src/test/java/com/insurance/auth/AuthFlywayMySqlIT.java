package com.insurance.auth;

import com.insurance.auth.client.CustomerClient;
import com.insurance.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Flyway scripts and the JPA mapping work on a REAL MySQL, not just on H2.
 * Runs only where Docker is available (CI, developer machines with Docker); skipped elsewhere.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
class AuthFlywayMySqlIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("auth_db");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @MockitoBean
    private CustomerClient customerClient;

    @Autowired
    private UserRepository userRepository;

    @Test
    void schemaMigratesAndBootstrapAdminExists() {
        assertThat(userRepository.findByEmailIgnoreCase("admin@test.local")).isPresent();
    }
}
