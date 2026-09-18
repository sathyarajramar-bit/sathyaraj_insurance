package com.insurance.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code auth.admin.*}: optional first ADMIN account, created at startup if it does not exist. */
@Getter
@Setter
@ConfigurationProperties(prefix = "auth.admin")
public class AdminBootstrapProperties {

    private String email;

    /** Never hard-coded: comes from ADMIN_PASSWORD. Empty = do not create an admin. */
    private String password;
}
