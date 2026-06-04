package com.naresh.urlshortener.rate;

import com.naresh.urlshortener.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);
    private static final String LUA = """
            local tokens_key = KEYS[1]
            local ts_key = KEYS[2]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_per_ms = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local ttl_ms = tonumber(ARGV[5])

            local tokens = tonumber(redis.call('GET', tokens_key))
            if tokens == nil then
                tokens = capacity
            end

            local last_refill = tonumber(redis.call('GET', ts_key))
            if last_refill == nil then
                last_refill = now
            end

            local delta = math.max(0, now - last_refill)
            tokens = math.min(capacity, tokens + (delta * refill_per_ms))

            local allowed = 0
            if tokens >= requested then
                allowed = 1
                tokens = tokens - requested
            end

            redis.call('SET', tokens_key, tokens, 'PX', ttl_ms)
            redis.call('SET', ts_key, now, 'PX', ttl_ms)

            local retry_after_ms = 0
            if allowed == 0 then
                retry_after_ms = math.ceil((requested - tokens) / refill_per_ms)
            end

            return {allowed, math.floor(tokens), retry_after_ms}
            """;

    private final StringRedisTemplate redis;
    private final AppProperties.RateLimit properties;

    RedisTokenBucketRateLimiter(StringRedisTemplate redis, AppProperties appProperties) {
        this.redis = redis;
        this.properties = appProperties.rateLimit();
    }

    @Override
    public RateLimitResult check(String key) {
        if (!properties.enabled()) {
            return new RateLimitResult(true, properties.requests(), 0);
        }

        long now = System.currentTimeMillis();
        long capacity = properties.requests();
        double refillPerMillisecond = capacity / (double) properties.window().toMillis();
        long ttlMilliseconds = properties.window().multipliedBy(2).toMillis();
        String tokensKey = "rl:tb:" + key + ":tokens";
        String timestampKey = "rl:tb:" + key + ":ts";

        try {
            @SuppressWarnings("unchecked")
            var result = (java.util.List<Long>) redis.execute((RedisCallback<Object>) connection ->
                    connection.scriptingCommands().eval(
                            LUA.getBytes(StandardCharsets.UTF_8),
                            org.springframework.data.redis.connection.ReturnType.MULTI,
                            2,
                            tokensKey.getBytes(StandardCharsets.UTF_8),
                            timestampKey.getBytes(StandardCharsets.UTF_8),
                            Long.toString(now).getBytes(StandardCharsets.UTF_8),
                            Long.toString(capacity).getBytes(StandardCharsets.UTF_8),
                            Double.toString(refillPerMillisecond).getBytes(StandardCharsets.UTF_8),
                            "1".getBytes(StandardCharsets.UTF_8),
                            Long.toString(ttlMilliseconds).getBytes(StandardCharsets.UTF_8)
                    ));
            if (result == null || result.size() < 3) {
                return new RateLimitResult(true, properties.requests(), 0);
            }
            boolean allowed = result.get(0) == 1;
            long retryAfterSeconds = Math.max(1, (long) Math.ceil(result.get(2) / 1000.0));
            return new RateLimitResult(allowed, result.get(1), allowed ? 0 : retryAfterSeconds);
        } catch (RuntimeException ex) {
            log.warn("Rate limiter failed for key={}; allowing request", key, ex);
            return new RateLimitResult(true, properties.requests(), 0);
        }
    }
}
