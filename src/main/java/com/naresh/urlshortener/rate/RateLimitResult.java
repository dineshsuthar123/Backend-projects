package com.naresh.urlshortener.rate;

public record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds) {
}
