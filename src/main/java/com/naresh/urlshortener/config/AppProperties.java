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
        Cache cache,
        RateLimit rateLimit,
        Analytics analytics
) {
    public AppProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8081";
        }
        if (codeLength == 0) {
            codeLength = 8;
        }
        if (cachePrefix == null || cachePrefix.isBlank()) {
            cachePrefix = "url:";
        }
        if (cache == null) {
            cache = new Cache(Duration.ofSeconds(5), Duration.ofMillis(80), Duration.ofMillis(400));
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(true, 120, Duration.ofMinutes(1));
        }
        if (analytics == null) {
            analytics = new Analytics(10_000, 500, Duration.ofSeconds(2), "change-me", false, "url-redirect-events");
        }
    }

    public record Cache(Duration stampedeLockTtl, Duration stampedePollDelay, Duration stampedeMaxWait) {
        public Cache {
            if (stampedeLockTtl == null || stampedeLockTtl.isZero() || stampedeLockTtl.isNegative()) {
                stampedeLockTtl = Duration.ofSeconds(5);
            }
            if (stampedePollDelay == null || stampedePollDelay.isZero() || stampedePollDelay.isNegative()) {
                stampedePollDelay = Duration.ofMillis(80);
            }
            if (stampedeMaxWait == null || stampedeMaxWait.isZero() || stampedeMaxWait.isNegative()) {
                stampedeMaxWait = Duration.ofMillis(400);
            }
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

    public record Analytics(@Min(100) int queueCapacity, @Min(1) int batchSize, Duration flushDelay, String hashSalt,
                            boolean durableEnabled, String topic) {
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
            if (topic == null || topic.isBlank()) {
                topic = "url-redirect-events";
            }
        }
    }
}
