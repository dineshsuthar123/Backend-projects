package com.naresh.urlshortener.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class UrlShortenerMetrics {

    private final MeterRegistry registry;
    private final Counter createCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter rateLimitedCounter;
    private final Counter databaseLookupCounter;
    private final ConcurrentMap<String, Counter> analyticsCounters = new ConcurrentHashMap<>();

    public UrlShortenerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.createCounter = Counter.builder("url.create")
                .description("Short URL creation attempts that reached persistence")
                .register(registry);
        this.cacheHitCounter = Counter.builder("url.cache.hit")
                .description("Redirect resolutions served from Redis")
                .register(registry);
        this.cacheMissCounter = Counter.builder("url.cache.miss")
                .description("Redirect resolutions that missed Redis")
                .register(registry);
        this.rateLimitedCounter = Counter.builder("url.rate.limited")
                .description("Requests rejected by the Redis token bucket")
                .register(registry);
        this.databaseLookupCounter = Counter.builder("url.database.lookup")
                .description("Redirect cache misses that queried PostgreSQL")
                .register(registry);
    }

    public void recordCreate() {
        createCounter.increment();
    }

    public void recordRedirect(boolean cacheHit, Duration latency) {
        Counter.builder("url.redirect")
                .tag("cache_hit", Boolean.toString(cacheHit))
                .description("Successful redirect responses")
                .register(registry)
                .increment();
        Timer.builder("url.redirect.latency")
                .tag("cache_hit", Boolean.toString(cacheHit))
                .description("Application-side redirect resolution latency")
                .register(registry)
                .record(latency);
    }

    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    public void recordRateLimited() {
        rateLimitedCounter.increment();
    }

    public void recordDatabaseLookup() {
        databaseLookupCounter.increment();
    }

    public void recordAnalyticsEvent(String outcome) {
        analyticsCounters.computeIfAbsent(outcome, key -> Counter.builder("url.analytics.events")
                        .tag("outcome", key)
                        .description("Analytics events by ingestion outcome")
                        .register(registry))
                .increment();
    }

    public void recordAnalyticsEvents(String outcome, int count) {
        analyticsCounters.computeIfAbsent(outcome, key -> Counter.builder("url.analytics.events")
                        .tag("outcome", key)
                        .description("Analytics events by ingestion outcome")
                        .register(registry))
                .increment(count);
    }
}
