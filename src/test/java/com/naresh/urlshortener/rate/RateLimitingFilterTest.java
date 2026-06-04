package com.naresh.urlshortener.rate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.naresh.urlshortener.observability.UrlShortenerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    @Test
    void allowedRequestContinuesFilterChainAndWritesRemainingHeader() throws Exception {
        RateLimitingFilter filter = filter(key -> new RateLimitResult(true, 7, 0));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/abc12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("7");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void blockedRequestReturnsMachineReadable429AndRetryAfter() throws Exception {
        RateLimitingFilter filter = filter(key -> new RateLimitResult(false, 0, 12));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/abc12345");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("12");
        assertThat(response.getContentAsString()).contains("TOO_MANY_REQUESTS");
    }

    @Test
    void actuatorRequestsBypassRateLimiter() throws Exception {
        RateLimitingFilter filter = filter(key -> {
            throw new AssertionError("actuator must not be rate limited");
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static RateLimitingFilter filter(RateLimiter limiter) {
        return new RateLimitingFilter(
                limiter,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                new UrlShortenerMetrics(new SimpleMeterRegistry())
        );
    }
}
