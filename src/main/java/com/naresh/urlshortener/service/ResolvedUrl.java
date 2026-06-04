package com.naresh.urlshortener.service;

public record ResolvedUrl(String shortCode, String originalUrl, boolean cacheHit) {
}
