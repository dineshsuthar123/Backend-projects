package com.naresh.urlshortener.api;

import com.naresh.urlshortener.service.AnalyticsService;
import com.naresh.urlshortener.observability.UrlShortenerMetrics;
import com.naresh.urlshortener.service.ResolvedUrl;
import com.naresh.urlshortener.service.UrlService;
import com.naresh.urlshortener.service.ProductionCreationService;
import com.naresh.urlshortener.config.PublicAccessProperties;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

@RestController
@RequestMapping
public class UrlController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;
    private final UrlShortenerMetrics metrics;
    private final ProductionCreationService productionCreation;
    private final PublicAccessProperties access;

    public UrlController(UrlService urlService, AnalyticsService analyticsService, UrlShortenerMetrics metrics,
                         ObjectProvider<ProductionCreationService> productionCreation, PublicAccessProperties access) {
        this.urlService = urlService;
        this.analyticsService = analyticsService;
        this.metrics = metrics;
        this.productionCreation = productionCreation.getIfAvailable();
        this.access = access;
    }

    @PostMapping("/api/v1/urls")
    public ResponseEntity<CreateShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        CreateShortUrlResponse response = productionCreation == null ? urlService.create(request)
                : productionCreation.create(request, false, "management");
        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }

    @GetMapping("/api/v1/config")
    public java.util.Map<String, Object> frontendConfig() {
        return java.util.Map.of("demo", access.enabled(), "demoEnabled", access.demoEnabled(),
                "demoHosts", access.demoHosts(), "analyticsRetentionDays", access.enabled() ? 7 : 0);
    }

    @PostMapping("/api/v1/demo/urls")
    public ResponseEntity<CreateShortUrlResponse> demo(@Valid @RequestBody CreateShortUrlRequest request,
                                                      HttpServletRequest servletRequest) {
        if (productionCreation == null || !access.demoEnabled()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Demo unavailable");
        }
        CreateShortUrlResponse response = productionCreation.create(request, true, servletRequest.getRemoteAddr());
        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }

    @GetMapping("/api/v1/urls/{shortCode:[A-Za-z0-9_-]{4,32}}/stats")
    public ResponseEntity<UrlStatsResponse> stats(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlService.stats(shortCode));
    }

    @DeleteMapping("/api/v1/urls/{shortCode:[A-Za-z0-9_-]{4,32}}/cache")
    public ResponseEntity<Void> evictCache(@PathVariable String shortCode) {
        urlService.evictCache(shortCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{shortCode:[A-Za-z0-9_-]{4,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        long started = System.nanoTime();
        ResolvedUrl resolvedUrl = urlService.resolve(shortCode);
        analyticsService.enqueue(shortCode, request);
        metrics.recordRedirect(resolvedUrl.cacheHit(), Duration.ofNanos(System.nanoTime() - started));
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, resolvedUrl.originalUrl())
                .build();
    }
}
