package com.naresh.urlshortener.service;

import com.naresh.urlshortener.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisCacheMissCoordinatorTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Test
    void onlyOneLoaderAcquiresSameShortCodeLock() {
        RedisCacheMissCoordinator coordinator = coordinator();

        Optional<CacheMissCoordinator.CacheLease> first = coordinator.tryAcquire("abc12345");
        Optional<CacheMissCoordinator.CacheLease> second = coordinator.tryAcquire("abc12345");

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        first.ifPresent(CacheMissCoordinator.CacheLease::close);
    }

    @Test
    void releasedLeaseAllowsNextLoader() {
        RedisCacheMissCoordinator coordinator = coordinator();

        CacheMissCoordinator.CacheLease first = coordinator.tryAcquire("release1").orElseThrow();
        first.close();

        assertThat(coordinator.tryAcquire("release1")).isPresent();
    }

    @Test
    void waitForCachedValueReturnsValueWarmedByLoader() {
        RedisCacheMissCoordinator coordinator = coordinator();
        InMemoryCache cache = new InMemoryCache();
        cache.put("abc12345", "https://example.com", null);

        Optional<String> value = coordinator.waitForCachedValue("abc12345", cache);

        assertThat(value).contains("https://example.com");
    }

    private static RedisCacheMissCoordinator coordinator() {
        return new RedisCacheMissCoordinator(redisTemplate(), properties());
    }

    private static StringRedisTemplate redisTemplate() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        return new StringRedisTemplate(connectionFactory);
    }

    private static AppProperties properties() {
        return new AppProperties(
                "http://localhost:8081",
                8,
                "url:",
                new AppProperties.Cache(Duration.ofSeconds(2), Duration.ofMillis(10), Duration.ofMillis(50)),
                new AppProperties.RateLimit(true, 120, Duration.ofMinutes(1)),
                new AppProperties.Analytics(1000, 100, Duration.ofMillis(100), "test", false, "url-redirect-events")
        );
    }

    private static class InMemoryCache implements UrlCache {
        private String value;

        @Override
        public Optional<String> get(String shortCode) {
            return Optional.ofNullable(value);
        }

        @Override
        public void put(String shortCode, String originalUrl, Duration ttl) {
            this.value = originalUrl;
        }

        @Override
        public void evict(String shortCode) {
            this.value = null;
        }
    }
}
