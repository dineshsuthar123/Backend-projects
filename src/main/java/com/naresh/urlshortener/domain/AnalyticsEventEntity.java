package com.naresh.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "analytics_events")
public class AnalyticsEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 16)
    private String shortCode;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent_hash", length = 64)
    private String userAgentHash;

    @Column(name = "referer", length = 1024)
    private String referer;

    protected AnalyticsEventEntity() {
    }

    public AnalyticsEventEntity(String shortCode, Instant occurredAt, String ipHash, String userAgentHash, String referer) {
        this.shortCode = shortCode;
        this.occurredAt = occurredAt;
        this.ipHash = ipHash;
        this.userAgentHash = userAgentHash;
        this.referer = referer;
    }
}
