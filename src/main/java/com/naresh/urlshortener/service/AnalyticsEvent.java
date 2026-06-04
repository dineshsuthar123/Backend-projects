package com.naresh.urlshortener.service;

import java.time.Instant;

record AnalyticsEvent(String shortCode, Instant occurredAt, String ipHash, String userAgentHash, String referer) {
}
