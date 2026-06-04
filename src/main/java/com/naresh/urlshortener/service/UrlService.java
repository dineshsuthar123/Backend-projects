package com.naresh.urlshortener.service;

import com.naresh.urlshortener.api.CreateShortUrlRequest;
import com.naresh.urlshortener.api.CreateShortUrlResponse;
import com.naresh.urlshortener.config.AppProperties;
import com.naresh.urlshortener.domain.UrlMapping;
import com.naresh.urlshortener.repository.UrlMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class UrlService {

    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final UrlMappingRepository repository;
    private final ShortCodeGenerator generator;
    private final UrlCache cache;
    private final AppProperties properties;

    public UrlService(UrlMappingRepository repository, ShortCodeGenerator generator, UrlCache cache, AppProperties properties) {
        this.repository = repository;
        this.generator = generator;
        this.cache = cache;
        this.properties = properties;
    }

    @Transactional
    public CreateShortUrlResponse create(CreateShortUrlRequest request) {
        Instant now = Instant.now();
        Instant expiresAt = request.ttlSeconds() == null ? null : now.plusSeconds(request.ttlSeconds());
        String shortCode = chooseShortCode(request.customAlias());

        UrlMapping saved = repository.save(new UrlMapping(shortCode, request.originalUrl(), now, expiresAt));
        cache.put(shortCode, saved.getOriginalUrl(), ttlFrom(now, saved.getExpiresAt()));

        return new CreateShortUrlResponse(
                saved.getShortCode(),
                properties.baseUrl() + "/" + saved.getShortCode(),
                saved.getOriginalUrl(),
                saved.getCreatedAt(),
                saved.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public ResolvedUrl resolve(String shortCode) {
        return cache.get(shortCode)
                .map(originalUrl -> new ResolvedUrl(shortCode, originalUrl, true))
                .orElseGet(() -> resolveFromDatabase(shortCode));
    }

    private ResolvedUrl resolveFromDatabase(String shortCode) {
        Instant now = Instant.now();
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (!mapping.isActive() || mapping.isExpired(now)) {
            throw new UrlExpiredException(shortCode);
        }

        cache.put(shortCode, mapping.getOriginalUrl(), ttlFrom(now, mapping.getExpiresAt()));
        return new ResolvedUrl(shortCode, mapping.getOriginalUrl(), false);
    }

    private String chooseShortCode(String customAlias) {
        if (customAlias != null && !customAlias.isBlank()) {
            if (repository.existsByShortCode(customAlias)) {
                throw new AliasAlreadyExistsException(customAlias);
            }
            return customAlias;
        }

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generator.generate(properties.codeLength());
            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique short code");
    }

    private static Duration ttlFrom(Instant now, Instant expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        return Duration.between(now, expiresAt);
    }
}
