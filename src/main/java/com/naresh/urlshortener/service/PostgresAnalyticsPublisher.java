package com.naresh.urlshortener.service;

import com.naresh.urlshortener.repository.AnalyticsEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A 302 is returned only after this transaction has committed. No in-memory fallback. */
@Component
@Profile("oracle")
public class PostgresAnalyticsPublisher implements AnalyticsPublisher {
    private final AnalyticsEventRepository repository;
    public PostgresAnalyticsPublisher(AnalyticsEventRepository repository) { this.repository = repository; }
    @Override @Transactional
    public boolean publish(AnalyticsEvent event) {
        repository.saveAndFlush(AnalyticsEventMapper.toEntity(event));
        return true;
    }
}
