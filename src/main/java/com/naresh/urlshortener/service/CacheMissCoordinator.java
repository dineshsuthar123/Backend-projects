package com.naresh.urlshortener.service;

import java.util.Optional;

public interface CacheMissCoordinator {

    Optional<CacheLease> tryAcquire(String shortCode);

    Optional<String> waitForCachedValue(String shortCode, UrlCache cache);

    interface CacheLease extends AutoCloseable {

        @Override
        void close();
    }
}
