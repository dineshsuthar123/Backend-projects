package com.naresh.urlshortener.service;

import com.naresh.urlshortener.config.AppProperties;
import com.naresh.urlshortener.domain.AnalyticsEventEntity;
import com.naresh.urlshortener.observability.UrlShortenerMetrics;
import com.naresh.urlshortener.repository.AnalyticsEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private final AnalyticsEventRepository repository = mock(AnalyticsEventRepository.class);
    private final UrlShortenerMetrics metrics = new UrlShortenerMetrics(new SimpleMeterRegistry());

    @Test
    void durablePublisherSuccessSkipsLocalQueue() {
        AnalyticsPublisher publisher = event -> true;
        AnalyticsService service = new AnalyticsService(repository, properties(true, 100), provider(publisher), metrics);

        service.enqueue("abc12345", request());
        service.flush();

        verify(repository, never()).saveAll(anyList());
        assertThat(service.droppedEvents()).isZero();
    }

    @Test
    void redpandaUnavailableFallsBackToLocalQueue() {
        AnalyticsPublisher unavailablePublisher = event -> false;
        AnalyticsService service = new AnalyticsService(repository, properties(true, 100), provider(unavailablePublisher), metrics);

        service.enqueue("abc12345", request());
        service.flush();

        verify(repository).saveAll(org.mockito.ArgumentMatchers.<List<AnalyticsEventEntity>>any());
        assertThat(service.droppedEvents()).isZero();
    }

    @Test
    void inMemoryQueueDropsEventsWhenFull() {
        AnalyticsService service = new AnalyticsService(repository, properties(false, 100), provider(null), metrics);

        for (int i = 0; i < 101; i++) {
            service.enqueue("abc12345", request());
        }

        assertThat(service.droppedEvents()).isEqualTo(1);
    }

    @Test
    void failedBatchPersistenceCountsDroppedEvents() {
        AnalyticsService service = new AnalyticsService(repository, properties(false, 100), provider(null), metrics);
        when(repository.saveAll(org.mockito.ArgumentMatchers.<List<AnalyticsEventEntity>>any()))
                .thenThrow(new RuntimeException("database unavailable"));

        service.enqueue("abc12345", request());
        service.flush();

        assertThat(service.droppedEvents()).isEqualTo(1);
    }

    private static AppProperties properties(boolean durableEnabled, int queueCapacity) {
        return new AppProperties(
                "http://localhost:8081",
                8,
                "url:",
                null,
                new AppProperties.RateLimit(false, 120, Duration.ofMinutes(1)),
                new AppProperties.Analytics(queueCapacity, 100, Duration.ofMillis(100), "test", durableEnabled, "url-redirect-events")
        );
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.1.1.1");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        return request;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AnalyticsPublisher> provider(AnalyticsPublisher publisher) {
        ObjectProvider<AnalyticsPublisher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        return provider;
    }
}
