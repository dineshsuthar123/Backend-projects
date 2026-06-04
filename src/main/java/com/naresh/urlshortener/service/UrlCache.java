package com.naresh.urlshortener.service;

import java.time.Duration;
import java.util.Optional;

public interface UrlCache {

    Optional<String> get(String shortCode);

    void put(String shortCode, String originalUrl, Duration ttl);
}
