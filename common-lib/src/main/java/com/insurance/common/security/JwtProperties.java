package com.insurance.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** {@code security.jwt.*}: the shared HS256 secret and token lifetimes (lifetimes are used by auth-service only). */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    @NotBlank
    @Size(min = 32, message = "security.jwt.secret must be at least 32 characters (256 bits)")
    private String secret;

    /** Issuer claim expected/produced. */
    private String issuer = "insurance-platform";

    private Duration accessTokenTtl = Duration.ofMinutes(15);

    private Duration refreshTokenTtl = Duration.ofDays(7);
}
