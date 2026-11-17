package com.naresh.urlshortener.service;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("oracle")
public class AnalyticsRetention {
    private final JdbcTemplate jdbc;
    public AnalyticsRetention(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Scheduled(fixedDelayString = "${app.analytics.retention-delay:60000}")
    @Transactional
    public void prune() {
        // Composite key is required: ctid alone is not unique across partitions.
        jdbc.update("""
            DELETE FROM analytics_events WHERE (id, occurred_at) IN
            (SELECT id, occurred_at FROM analytics_events WHERE occurred_at < CURRENT_TIMESTAMP - INTERVAL '7 days'
             ORDER BY occurred_at LIMIT 5000)
            """);
        jdbc.update("DELETE FROM demo_creation_budgets WHERE budget_date < CURRENT_DATE - 2");
    }
}
