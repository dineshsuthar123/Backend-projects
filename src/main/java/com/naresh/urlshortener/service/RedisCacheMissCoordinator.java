package com.naresh.urlshortener.service;

import com.naresh.urlshortener.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
class RedisCacheMissCoordinator implements CacheMissCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheMissCoordinator.class);
    private static final String UNLOCK_LUA = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private final StringRedisTemplate redis;
    private final AppProperties.Cache properties;

    RedisCacheMissCoordinator(StringRedisTemplate redis, AppProperties appProperties) {
        this.redis = redis;
        this.properties = appProperties.cache();
    }

    @Override
    public Optional<CacheLease> tryAcquire(String shortCode) {
        String key = lockKey(shortCode);
        String owner = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, owner, properties.stampedeLockTtl());
            if (Boolean.TRUE.equals(acquired)) {
                return Optional.of(() -> release(key, owner));
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Cache stampede lock failed for shortCode={}; continuing without lock", shortCode, ex);
            return Optional.of(() -> {
            });
        }
    }

    @Override
    public Optional<String> waitForCachedValue(String shortCode, UrlCache cache) {
        long deadline = System.nanoTime() + properties.stampedeMaxWait().toNanos();
        while (System.nanoTime() < deadline) {
            sleep(properties.stampedePollDelay());
            Optional<String> cached = cache.get(shortCode);
            if (cached.isPresent()) {
                return cached;
            }
        }
        return Optional.empty();
    }

    private void release(String key, String owner) {
        try {
            redis.execute((RedisCallback<Object>) connection ->
                    connection.scriptingCommands().eval(
                            UNLOCK_LUA.getBytes(StandardCharsets.UTF_8),
                            org.springframework.data.redis.connection.ReturnType.INTEGER,
                            1,
                            key.getBytes(StandardCharsets.UTF_8),
                            owner.getBytes(StandardCharsets.UTF_8)
                    ));
        } catch (RuntimeException ex) {
            log.warn("Cache stampede unlock failed for key={}", key, ex);
        }
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }
    }

    private static String lockKey(String shortCode) {
        return "lock:url:" + shortCode;
    }
}
