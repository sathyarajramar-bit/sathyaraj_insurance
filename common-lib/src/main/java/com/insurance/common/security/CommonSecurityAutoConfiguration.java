package com.insurance.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT "resource server" setup shared by every business service.
 *
 * <p>A service only needs {@code security.jwt.secret} and optionally {@code security.public-paths}
 * in its configuration. Any bean here can be overridden by declaring one of the same type in the
 * service ({@code @ConditionalOnMissingBean}).
 */
@AutoConfiguration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, SecurityPublicPathsProperties.class})
public class CommonSecurityAutoConfiguration {

    static final String[] ALWAYS_PUBLIC = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    @ConditionalOnMissingBean
    JwtTokenVerifier jwtTokenVerifier(JwtProperties properties) {
        return new JwtTokenVerifier(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    JsonSecurityErrorHandlers jsonSecurityErrorHandlers(ObjectMapper objectMapper) {
        return new JsonSecurityErrorHandlers(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ServiceTokenProvider serviceTokenProvider(JwtProperties properties,
                                              @Value("${spring.application.name:service}") String serviceName) {
        return new ServiceTokenProvider(properties, serviceName);
    }

    @Bean
    @ConditionalOnMissingBean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenVerifier verifier,
                                            JsonSecurityErrorHandlers errorHandlers,
                                            SecurityPublicPathsProperties publicPaths) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(errorHandlers).accessDeniedHandler(errorHandlers))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(ALWAYS_PUBLIC).permitAll();
                    for (SecurityPublicPathsProperties.PublicPath path : publicPaths.parsedPublicPaths()) {
                        if (path.method() == null) {
                            auth.requestMatchers(path.pattern()).permitAll();
                        } else {
                            auth.requestMatchers(path.method(), path.pattern()).permitAll();
                        }
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(new JwtAuthenticationFilter(verifier, errorHandlers),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
