package com.naresh.urlshortener.service;

import com.naresh.urlshortener.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.analytics", name = "durable-enabled", havingValue = "true")
class KafkaAnalyticsPublisher implements AnalyticsPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAnalyticsPublisher.class);

    private final KafkaTemplate<String, AnalyticsEvent> kafkaTemplate;
    private final String topic;

    KafkaAnalyticsPublisher(KafkaTemplate<String, AnalyticsEvent> kafkaTemplate, AppProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = properties.analytics().topic();
    }

    @Override
    public boolean publish(AnalyticsEvent event) {
        try {
            kafkaTemplate.send(topic, event.shortCode(), event)
                    .whenComplete((ignored, ex) -> {
                        if (ex != null) {
                            log.warn("Analytics publish failed for shortCode={}", event.shortCode(), ex);
                        }
                    });
            return true;
        } catch (RuntimeException ex) {
            log.warn("Analytics publish could not be started for shortCode={}", event.shortCode(), ex);
            return false;
        }
    }
}
