package com.insurance.common.client;

import com.insurance.common.security.ServiceTokenProvider;
import com.insurance.common.web.CorrelationIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/**
 * Applies to every Feign client in a service (parent-context bean): adds the service JWT and forwards
 * the correlation id so one request can be traced across service hops.
 * Only active when OpenFeign is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class CommonFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "serviceAuthRequestInterceptor")
    RequestInterceptor serviceAuthRequestInterceptor(ServiceTokenProvider tokenProvider) {
        return template -> {
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken());
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null) {
                template.header(CorrelationIdFilter.HEADER, correlationId);
            }
        };
    }
}
