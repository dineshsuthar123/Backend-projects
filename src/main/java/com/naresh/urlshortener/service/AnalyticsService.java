package com.naresh.urlshortener.service;

import com.naresh.urlshortener.config.AppProperties;
import com.naresh.urlshortener.domain.AnalyticsEventEntity;
import com.naresh.urlshortener.repository.AnalyticsEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.LongAdder;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final BlockingQueue<AnalyticsEvent> queue;
    private final AnalyticsEventRepository repository;
    private final AppProperties.Analytics properties;
    private final AnalyticsPublisher analyticsPublisher;
    private final LongAdder droppedEvents = new LongAdder();

    public AnalyticsService(AnalyticsEventRepository repository, AppProperties appProperties,
                            ObjectProvider<AnalyticsPublisher> analyticsPublisher) {
        this.repository = repository;
        this.properties = appProperties.analytics();
        this.analyticsPublisher = analyticsPublisher.getIfAvailable();
        this.queue = new ArrayBlockingQueue<>(this.properties.queueCapacity());
    }

    public void enqueue(String shortCode, HttpServletRequest request) {
        AnalyticsEvent event = new AnalyticsEvent(
                shortCode,
                Instant.now(),
                sha256(clientIp(request), properties.hashSalt()),
                sha256(header(request, "User-Agent"), properties.hashSalt()),
                truncate(header(request, "Referer"), 1024)
        );

        if (properties.durableEnabled() && analyticsPublisher != null && analyticsPublisher.publish(event)) {
            return;
        }

        if (!queue.offer(event)) {
            droppedEvents.increment();
        }
    }

    @Scheduled(fixedDelayString = "${app.analytics.flush-delay:2000}")
    public void flush() {
        List<AnalyticsEvent> events = new ArrayList<>(properties.batchSize());
        queue.drainTo(events, properties.batchSize());
        if (events.isEmpty()) {
            return;
        }

        List<AnalyticsEventEntity> entities = events.stream()
                .map(AnalyticsEventMapper::toEntity)
                .toList();

        try {
            repository.saveAll(entities);
        } catch (RuntimeException ex) {
            log.error("Failed to persist {} analytics events", events.size(), ex);
            droppedEvents.add(events.size());
        }
    }

    public long droppedEvents() {
        return droppedEvents.sum();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = header(request, "X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String sha256(String value, String salt) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((salt + ":" + value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
