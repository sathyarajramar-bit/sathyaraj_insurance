package com.insurance.common.web;

import com.insurance.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Registers the cross-cutting web pieces for every servlet based service:
 * standard error handling, correlation-id logging and OpenAPI defaults.
 *
 * <p>Registered through {@code META-INF/spring/...AutoConfiguration.imports}, so services do not need to
 * component-scan the {@code com.insurance.common} package.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({GlobalExceptionHandler.class, OpenApiConfig.class})
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
