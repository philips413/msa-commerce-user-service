package com.devbook.user;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("devbook.auth")
record AuthProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Path privateKeyPath,
        Path publicKeyPath,
        boolean cookieSecure) {}
