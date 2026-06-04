package com.naresh.urlshortener.rate;

public interface RateLimiter {

    RateLimitResult check(String key);
}
