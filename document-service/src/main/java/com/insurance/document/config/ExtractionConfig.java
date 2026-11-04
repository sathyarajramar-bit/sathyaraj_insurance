package com.insurance.document.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Time source for date-derived fields (driving experience in years), so tests can pin "today". */
@Configuration
public class ExtractionConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock extractionClock() {
        return Clock.systemUTC();
    }
}
