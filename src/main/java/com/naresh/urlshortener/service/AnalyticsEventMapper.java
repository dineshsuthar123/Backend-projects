package com.naresh.urlshortener.service;

import com.naresh.urlshortener.domain.AnalyticsEventEntity;

final class AnalyticsEventMapper {

    private AnalyticsEventMapper() {
    }

    static AnalyticsEventEntity toEntity(AnalyticsEvent event) {
        return new AnalyticsEventEntity(
                event.shortCode(),
                event.occurredAt(),
                event.ipHash(),
                event.userAgentHash(),
                event.referer()
        );
    }
}
