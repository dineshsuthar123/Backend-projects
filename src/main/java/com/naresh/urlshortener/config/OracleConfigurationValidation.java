package com.naresh.urlshortener.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
@Profile("oracle")
public class OracleConfigurationValidation {
    public OracleConfigurationValidation(AppProperties app, PublicAccessProperties access) {
        URI base = URI.create(app.baseUrl());
        if (!"https".equals(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                || !(base.getPath() == null || base.getPath().isEmpty()) || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("Oracle requires an absolute HTTPS public base URL without a path");
        }
        if (!access.enabled() || !app.analytics().durableEnabled() || !app.rateLimit().enabled()
                || app.analytics().hashSalt().length() < 32) {
            throw new IllegalArgumentException("Oracle requires access controls, durable analytics, rate limiting and a strong analytics salt");
        }
    }
}
