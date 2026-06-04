package com.naresh.urlshortener.service;

import com.naresh.urlshortener.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
class RedisUrlCache implements UrlCache {

    private static final Logger log = LoggerFactory.getLogger(RedisUrlCache.class);

    private final StringRedisTemplate redis;
    private final String cachePrefix;

    RedisUrlCache(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.cachePrefix = properties.cachePrefix();
    }

    @Override
    public Optional<String> get(String shortCode) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key(shortCode)));
        } catch (RuntimeException ex) {
            log.warn("Redis read failed for shortCode={}", shortCode, ex);
            return Optional.empty();
        }
    }

    @Override
    public void put(String shortCode, String originalUrl, Duration ttl) {
        try {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                redis.opsForValue().set(key(shortCode), originalUrl);
            } else {
                redis.opsForValue().set(key(shortCode), originalUrl, ttl);
            }
        } catch (RuntimeException ex) {
            log.warn("Redis write failed for shortCode={}", shortCode, ex);
        }
    }

    @Override
    public void evict(String shortCode) {
        try {
            redis.delete(key(shortCode));
        } catch (RuntimeException ex) {
            log.warn("Redis evict failed for shortCode={}", shortCode, ex);
        }
    }

    private String key(String shortCode) {
        return cachePrefix + shortCode;
    }
}
