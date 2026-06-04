package com.naresh.urlshortener.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String baseUrl,
        @Min(6) @Max(16) int codeLength,
        String cachePrefix,
        RateLimit rateLimit,
        Analytics analytics
) {
    public AppProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        if (codeLength == 0) {
            codeLength = 8;
        }
        if (cachePrefix == null || cachePrefix.isBlank()) {
            cachePrefix = "url:";
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(true, 120, Duration.ofMinutes(1));
        }
        if (analytics == null) {
            analytics = new Analytics(10_000, 500, Duration.ofSeconds(2), "change-me");
        }
    }

    public record RateLimit(boolean enabled, @Min(1) int requests, Duration window) {
        public RateLimit {
            if (requests == 0) {
                requests = 120;
            }
            if (window == null || window.isZero() || window.isNegative()) {
                window = Duration.ofMinutes(1);
            }
        }
    }

    public record Analytics(@Min(100) int queueCapacity, @Min(1) int batchSize, Duration flushDelay, String hashSalt) {
        public Analytics {
            if (queueCapacity == 0) {
                queueCapacity = 10_000;
            }
            if (batchSize == 0) {
                batchSize = 500;
            }
            if (flushDelay == null || flushDelay.isZero() || flushDelay.isNegative()) {
                flushDelay = Duration.ofSeconds(2);
            }
            if (hashSalt == null || hashSalt.isBlank()) {
                hashSalt = "change-me";
            }
        }
    }
}
