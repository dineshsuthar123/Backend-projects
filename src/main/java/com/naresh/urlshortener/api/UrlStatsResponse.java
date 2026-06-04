package com.naresh.urlshortener.api;

public record UrlStatsResponse(
        String shortCode,
        String originalUrl,
        boolean cacheHit,
        long redirectEvents
) {
}
