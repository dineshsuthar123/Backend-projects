package com.naresh.urlshortener.service;

import com.naresh.urlshortener.repository.AnalyticsEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.analytics", name = "durable-enabled", havingValue = "true")
class DurableAnalyticsConsumer {

    private final AnalyticsEventRepository repository;

    DurableAnalyticsConsumer(AnalyticsEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "${app.analytics.topic:url-redirect-events}", groupId = "${spring.kafka.consumer.group-id:url-shortener-analytics}")
    void consume(List<AnalyticsEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        repository.saveAll(events.stream().map(AnalyticsEventMapper::toEntity).toList());
    }
}
