package com.naresh.urlshortener.service;

import java.time.Instant;

public record AnalyticsEvent(String shortCode, Instant occurredAt, String ipHash, String userAgentHash, String referer) {
}
