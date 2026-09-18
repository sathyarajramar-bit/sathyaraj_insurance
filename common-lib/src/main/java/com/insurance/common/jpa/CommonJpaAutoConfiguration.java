package com.insurance.common.jpa;

import com.insurance.common.security.CurrentUser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Enables JPA auditing and resolves the current auditor from the JWT principal.
 * Background jobs and service-to-service calls without a user are recorded as "system".
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(name = "jakarta.persistence.EntityManager")
@EnableJpaAuditing(auditorAwareRef = "platformAuditorAware")
public class CommonJpaAutoConfiguration {

    @Bean(name = "platformAuditorAware")
    @ConditionalOnMissingBean(name = "platformAuditorAware")
    AuditorAware<String> platformAuditorAware() {
        return () -> Optional.of(CurrentUser.get()
                .map(user -> user.hasRole("SERVICE") ? user.email() : String.valueOf(user.userId()))
                .orElse("system"));
    }
}
