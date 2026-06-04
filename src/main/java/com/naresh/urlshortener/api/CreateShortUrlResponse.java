package com.naresh.urlshortener.api;

import java.time.Instant;

public record CreateShortUrlResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt
) {
}
