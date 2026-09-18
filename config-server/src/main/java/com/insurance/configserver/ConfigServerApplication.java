package com.insurance.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Centralised configuration for every microservice.
 *
 * <p>Services ask this server for their configuration at startup using
 * {@code spring.config.import=configserver:...}. The server merges, in order of precedence:
 * <ol>
 *   <li>{@code {application}-{profile}.yml} (e.g. api-gateway-docker.yml)</li>
 *   <li>{@code {application}.yml}          (e.g. api-gateway.yml)</li>
 *   <li>{@code application-{profile}.yml}  (shared, profile specific)</li>
 *   <li>{@code application.yml}            (shared by all services)</li>
 * </ol>
 *
 * <p>Interview notes:
 * <ul>
 *   <li><b>Problem solved:</b> with 8+ services, duplicating Eureka URLs, actuator settings, timeouts and
 *       feature flags in every jar is error-prone. A change (e.g. a timeout) should not need 8 rebuilds.</li>
 *   <li><b>Backends:</b> this project uses the {@code native} backend (files on the classpath / a folder)
 *       to keep local setup simple. In production point {@code spring.cloud.config.server.git.uri} at a Git
 *       repository to get history, code review and rollback of configuration for free.</li>
 *   <li><b>Failure mode:</b> clients only need the server at startup (and on refresh). If it is down, a
 *       client with {@code fail-fast=false} starts with its local defaults; with {@code fail-fast=true}
 *       it retries and then refuses to start, which is safer for production.</li>
 *   <li><b>Runtime refresh:</b> {@code POST /actuator/refresh} on a client re-reads {@code @RefreshScope}
 *       beans; Spring Cloud Bus can broadcast that to all instances (not included, to avoid a broker).</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
