package com.naresh.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Set;

@ConfigurationProperties(prefix = "app.security")
public record PublicAccessProperties(boolean enabled, String managementKey, boolean demoEnabled,
                                     Set<String> demoHosts, int demoDailyLimit, int demoIpDailyLimit,
                                     int maxMappings) {
    public PublicAccessProperties {
        if (enabled && (managementKey == null || managementKey.length() < 32)) {
            throw new IllegalArgumentException("A management key of at least 32 characters is required");
        }
        demoHosts = demoHosts == null ? Set.of("example.com", "spring.io") : Set.copyOf(demoHosts);
        demoDailyLimit = demoDailyLimit > 0 ? demoDailyLimit : 100;
        demoIpDailyLimit = demoIpDailyLimit > 0 ? demoIpDailyLimit : 10;
        maxMappings = maxMappings > 0 ? maxMappings : 10_000;
    }

    @Override public String toString() { return "PublicAccessProperties[credentials redacted]"; }
}
