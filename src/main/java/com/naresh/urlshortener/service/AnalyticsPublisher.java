package com.naresh.urlshortener.service;

public interface AnalyticsPublisher {

    boolean publish(AnalyticsEvent event);
}
