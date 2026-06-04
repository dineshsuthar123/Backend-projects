package com.naresh.urlshortener.rate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naresh.urlshortener.api.ErrorResponse;
import com.naresh.urlshortener.observability.UrlShortenerMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final UrlShortenerMetrics metrics;

    public RateLimitingFilter(RateLimiter rateLimiter, ObjectMapper objectMapper, UrlShortenerMetrics metrics) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RateLimitResult result = rateLimiter.check(clientKey(request));
        response.setHeader("X-RateLimit-Remaining", Long.toString(result.remaining()));

        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        metrics.recordRateLimited();
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(result.retryAfterSeconds()));
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(429, "TOO_MANY_REQUESTS", "Rate limit exceeded"));
    }

    private static String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
