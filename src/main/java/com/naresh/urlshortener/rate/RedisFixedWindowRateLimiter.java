package com.naresh.urlshortener.rate;

import com.naresh.urlshortener.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class RedisFixedWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisFixedWindowRateLimiter.class);

    private final StringRedisTemplate redis;
    private final AppProperties.RateLimit properties;

    RedisFixedWindowRateLimiter(StringRedisTemplate redis, AppProperties appProperties) {
        this.redis = redis;
        this.properties = appProperties.rateLimit();
    }

    @Override
    public RateLimitResult check(String key) {
        if (!properties.enabled()) {
            return new RateLimitResult(true, properties.requests(), 0);
        }

        long epochSeconds = Instant.now().getEpochSecond();
        long windowSeconds = properties.window().toSeconds();
        long window = epochSeconds / windowSeconds;
        String redisKey = "rl:" + key + ":" + window;

        try {
            Long used = redis.opsForValue().increment(redisKey);
            if (used != null && used == 1) {
                redis.expire(redisKey, properties.window().plusSeconds(1));
            }

            long count = used == null ? 1 : used;
            long remaining = Math.max(0, properties.requests() - count);
            long retryAfter = windowSeconds - (epochSeconds % windowSeconds);
            return new RateLimitResult(count <= properties.requests(), remaining, retryAfter);
        } catch (RuntimeException ex) {
            log.warn("Rate limiter failed for key={}; allowing request", key, ex);
            return new RateLimitResult(true, properties.requests(), 0);
        }
    }
}
