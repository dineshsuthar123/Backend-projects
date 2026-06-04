package com.naresh.urlshortener.service;

import com.naresh.urlshortener.api.CreateShortUrlRequest;
import com.naresh.urlshortener.api.CreateShortUrlResponse;
import com.naresh.urlshortener.config.AppProperties;
import com.naresh.urlshortener.domain.UrlMapping;
import com.naresh.urlshortener.observability.UrlShortenerMetrics;
import com.naresh.urlshortener.repository.AnalyticsEventRepository;
import com.naresh.urlshortener.repository.UrlMappingRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UrlServiceTest {

    private final UrlMappingRepository repository = mock(UrlMappingRepository.class);
    private final AnalyticsEventRepository analyticsEventRepository = mock(AnalyticsEventRepository.class);
    private final FakeUrlCache cache = new FakeUrlCache();
    private final CacheMissCoordinator cacheMissCoordinator = new FakeCacheMissCoordinator();
    private final UrlShortenerMetrics metrics = new UrlShortenerMetrics(new SimpleMeterRegistry());
    private final AppProperties properties = new AppProperties(
            "http://sho.rt",
            8,
            "url:",
            null,
            new AppProperties.RateLimit(false, 120, Duration.ofMinutes(1)),
            new AppProperties.Analytics(1000, 100, Duration.ofSeconds(2), "test", false, "url-redirect-events")
    );

    @Test
    void createStoresMappingAndWarmsCache() {
        ShortCodeGenerator generator = fixedGenerator("abc12345");
        UrlService service = service(generator, cacheMissCoordinator);
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com/a", null, 3600L);

        when(repository.existsByShortCode("abc12345")).thenReturn(false);
        when(repository.save(org.mockito.ArgumentMatchers.any(UrlMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateShortUrlResponse response = service.create(request);

        assertThat(response.shortCode()).isEqualTo("abc12345");
        assertThat(response.shortUrl()).isEqualTo("http://sho.rt/abc12345");
        assertThat(cache.get("abc12345")).contains("https://example.com/a");
    }

    @Test
    void resolveReturnsCacheHitWithoutRepositoryLookup() {
        UrlService service = service(fixedGenerator("unused"), cacheMissCoordinator);
        cache.put("abc12345", "https://example.com/cached", null);

        ResolvedUrl resolved = service.resolve("abc12345");

        assertThat(resolved.originalUrl()).isEqualTo("https://example.com/cached");
        assertThat(resolved.cacheHit()).isTrue();
        verify(repository, never()).findByShortCode("abc12345");
    }

    @Test
    void resolveRejectsExpiredDatabaseMapping() {
        UrlService service = service(fixedGenerator("unused"), cacheMissCoordinator);
        UrlMapping expired = new UrlMapping(
                "expired1",
                "https://example.com/old",
                Instant.now().minus(Duration.ofDays(2)),
                Instant.now().minus(Duration.ofDays(1))
        );

        when(repository.findByShortCode("expired1")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resolve("expired1"))
                .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void createRejectsDuplicateCustomAlias() {
        UrlService service = service(fixedGenerator("unused"), cacheMissCoordinator);
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com/a", "taken1", null);

        when(repository.existsByShortCode("taken1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(AliasAlreadyExistsException.class);
    }

    @Test
    void evictCacheRemovesExistingCachedMapping() {
        UrlService service = service(fixedGenerator("unused"), cacheMissCoordinator);
        cache.put("abc12345", "https://example.com/cached", null);
        when(repository.existsByShortCode("abc12345")).thenReturn(true);

        service.evictCache("abc12345");

        assertThat(cache.get("abc12345")).isEmpty();
    }

    @Test
    void cacheMissUsesWarmedValueWhenAnotherRequestOwnsLoad() {
        CacheMissCoordinator warmingCoordinator = new CacheMissCoordinator() {
            @Override
            public Optional<CacheLease> tryAcquire(String shortCode) {
                return Optional.empty();
            }

            @Override
            public Optional<String> waitForCachedValue(String shortCode, UrlCache cache) {
                cache.put(shortCode, "https://example.com/warmed", null);
                return cache.get(shortCode);
            }
        };
        UrlService service = service(fixedGenerator("unused"), warmingCoordinator);

        ResolvedUrl resolved = service.resolve("abc12345");

        assertThat(resolved.originalUrl()).isEqualTo("https://example.com/warmed");
        assertThat(resolved.cacheHit()).isTrue();
        verify(repository, never()).findByShortCode("abc12345");
    }

    private UrlService service(ShortCodeGenerator generator, CacheMissCoordinator coordinator) {
        return new UrlService(repository, analyticsEventRepository, generator, cache, coordinator, properties, metrics);
    }

    private static ShortCodeGenerator fixedGenerator(String value) {
        return new ShortCodeGenerator() {
            @Override
            public String generate(int length) {
                return value;
            }
        };
    }

    private static class FakeUrlCache implements UrlCache {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public Optional<String> get(String shortCode) {
            return Optional.ofNullable(values.get(shortCode));
        }

        @Override
        public void put(String shortCode, String originalUrl, Duration ttl) {
            values.put(shortCode, originalUrl);
        }

        @Override
        public void evict(String shortCode) {
            values.remove(shortCode);
        }
    }

    private static class FakeCacheMissCoordinator implements CacheMissCoordinator {

        @Override
        public Optional<CacheLease> tryAcquire(String shortCode) {
            return Optional.of(() -> {
            });
        }

        @Override
        public Optional<String> waitForCachedValue(String shortCode, UrlCache cache) {
            return cache.get(shortCode);
        }
    }
}
