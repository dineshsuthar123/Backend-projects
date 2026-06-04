package com.naresh.urlshortener.rate;

import com.naresh.urlshortener.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisTokenBucketRateLimiterTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Test
    void allowsRequestsWithinCapacity() {
        RedisTokenBucketRateLimiter limiter = limiter(2, Duration.ofSeconds(10));

        assertThat(limiter.check("allowed-client").allowed()).isTrue();
        assertThat(limiter.check("allowed-client").allowed()).isTrue();
    }

    @Test
    void blocksWhenBucketIsEmpty() {
        RedisTokenBucketRateLimiter limiter = limiter(1, Duration.ofSeconds(10));

        RateLimitResult first = limiter.check("blocked-client");
        RateLimitResult second = limiter.check("blocked-client");

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isFalse();
        assertThat(second.retryAfterSeconds()).isPositive();
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        RedisTokenBucketRateLimiter limiter = limiter(1, Duration.ofMillis(100));

        assertThat(limiter.check("refill-client").allowed()).isTrue();
        assertThat(limiter.check("refill-client").allowed()).isFalse();
        Thread.sleep(150);
        assertThat(limiter.check("refill-client").allowed()).isTrue();
    }

    private static RedisTokenBucketRateLimiter limiter(int requests, Duration window) {
        return new RedisTokenBucketRateLimiter(redisTemplate(), properties(requests, window));
    }

    private static StringRedisTemplate redisTemplate() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        return new StringRedisTemplate(connectionFactory);
    }

    private static AppProperties properties(int requests, Duration window) {
        return new AppProperties(
                "http://localhost:8081",
                8,
                "url:",
                null,
                new AppProperties.RateLimit(true, requests, window),
                new AppProperties.Analytics(1000, 100, Duration.ofMillis(100), "test", false, "url-redirect-events")
        );
    }
}
