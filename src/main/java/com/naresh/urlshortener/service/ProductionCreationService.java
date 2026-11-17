package com.naresh.urlshortener.service;

import com.naresh.urlshortener.api.*;
import com.naresh.urlshortener.config.PublicAccessProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

@Service
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
public class ProductionCreationService {
    private final UrlService urls;
    private final JdbcTemplate jdbc;
    private final PublicAccessProperties properties;
    public ProductionCreationService(UrlService urls, JdbcTemplate jdbc, PublicAccessProperties properties) {
        this.urls = urls; this.jdbc = jdbc; this.properties = properties;
    }
    @Transactional
    public CreateShortUrlResponse create(CreateShortUrlRequest request, boolean demo, String clientIp) {
        // Serialize the small creation workload, including quota and mapping writes, across restarts.
        jdbc.execute("SELECT pg_advisory_xact_lock(78542119)");
        Long count = jdbc.queryForObject("SELECT count(*) FROM urls", Long.class);
        if (count != null && count >= properties.maxMappings()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Mapping storage limit reached; contact the operator");
        }
        if (demo) {
            String host = UrlPolicy.validate(request.originalUrl()).getHost().toLowerCase(Locale.ROOT);
            if (!properties.demoEnabled() || !properties.demoHosts().contains(host)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demo destinations are limited to " + String.join(", ", properties.demoHosts()));
            }
            Long ttl = request.ttlSeconds() == null ? 86400L : request.ttlSeconds();
            if (ttl > 86400) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demo TTL cannot exceed one day");
            takeBudget("global", properties.demoDailyLimit());
            takeBudget(hash(clientIp), properties.demoIpDailyLimit());
            request = new CreateShortUrlRequest(request.originalUrl(), request.customAlias(), ttl);
        }
        return urls.create(request);
    }
    private void takeBudget(String identity, int limit) {
        int updated = jdbc.update("""
                INSERT INTO demo_creation_budgets (budget_date, identity, used)
                VALUES ((CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date, ?, 1)
                ON CONFLICT (budget_date, identity) DO UPDATE SET used = demo_creation_budgets.used + 1
                WHERE demo_creation_budgets.used < ?
                """, identity, limit);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily demo creation limit reached");
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
