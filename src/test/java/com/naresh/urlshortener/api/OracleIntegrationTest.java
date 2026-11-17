package com.naresh.urlshortener.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naresh.urlshortener.service.AnalyticsRetention;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("oracle")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class OracleIntegrationTest {
    private static final String KEY = "test-only-management-key-1234567890123456";
    @Container static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate cache;
    @Autowired AnalyticsRetention retention;
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.flyway.url", pg::getJdbcUrl);
        r.add("spring.flyway.user", pg::getUsername);
        r.add("spring.flyway.password", pg::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("app.base-url", () -> "https://short.example.test");
        r.add("app.security.management-key", () -> KEY);
        r.add("app.security.demo-ip-daily-limit", () -> 2);
        r.add("app.analytics.hash-salt", () -> "integration-test-salt-01234567890123456789");
        r.add("app.analytics.retention-delay", () -> 3600000);
    }
    @BeforeEach void clearTrafficAndBudgets() {
        cache.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb(); return null;
        });
        jdbc.update("DELETE FROM demo_creation_budgets");
    }
    @Test void privateCreationPublicRedirectAndDurableAnalytics() throws Exception {
        assertThat(send("POST", "/api/v1/urls", Map.of("originalUrl", "https://example.com"), false).statusCode()).isEqualTo(401);
        String alias = "alias_32_" + "a".repeat(23);
        var created = send("POST", "/api/v1/urls", Map.of("originalUrl", "https://example.com/path", "customAlias", alias), true);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("https://short.example.test/" + alias);
        assertThat(send("GET", "/" + alias, null, false).statusCode()).isEqualTo(302);
        // No await: the event is durable before the redirect response is returned.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_events WHERE short_code=?", Long.class, alias)).isEqualTo(1);
        assertThat(send("GET", "/api/v1/urls/" + alias + "/stats", null, false).body()).contains("\"redirectEvents\":1");
        assertThat(send("DELETE", "/api/v1/urls/" + alias + "/cache", null, false).statusCode()).isEqualTo(401);
        assertThat(send("DELETE", "/api/v1/urls/" + alias + "/cache", null, true).statusCode()).isEqualTo(204);
    }
    @Test void demoHasDestinationTtlAndDurableIpBudgets() throws Exception {
        assertThat(send("POST", "/api/v1/demo/urls", Map.of("originalUrl", "https://evil.example"), false).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/v1/demo/urls", Map.of("originalUrl", "https://example.com", "ttlSeconds", 86401), false).statusCode()).isEqualTo(400);
        for (int i=0; i<2; i++) assertThat(send("POST", "/api/v1/demo/urls", Map.of("originalUrl", "https://example.com", "ttlSeconds", 1), false).statusCode()).isEqualTo(201);
        // Redis loss/restart and changing XFF cannot reset the PostgreSQL daily budget.
        cache.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> { connection.serverCommands().flushDb(); return null; });
        assertThat(send("POST", "/api/v1/demo/urls", Map.of("originalUrl", "https://example.com"), false).statusCode()).isEqualTo(429);
    }
    @Test void hiddenEndpointsSafeErrorsAndFrontend() throws Exception {
        assertThat(send("GET", "/", null, false).body()).contains("Short URL Builder").doesNotContain("burstButton", "evictButton");
        assertThat(send("GET", "/app.js", null, false).body()).doesNotContain("innerHTML", KEY);
        for (String path : java.util.List.of("metrics", "prometheus", "env", "beans")) {
            assertThat(send("GET", "/actuator/" + path, null, false).statusCode()).isEqualTo(404);
        }
        assertThat(send("POST", "/api/v1/urls", Map.of("originalUrl", "https://user:password@example.com"), true).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/v1/urls", Map.of("originalUrl", "https://example.com", "customAlias", "actuator"), true).statusCode()).isEqualTo(400);
    }
    @Test void ttlExpiryAndFutureDefaultPartitionWork() throws Exception {
        String alias = "ttl_oracle";
        assertThat(send("POST", "/api/v1/urls", Map.of("originalUrl", "https://example.com", "customAlias", alias, "ttlSeconds", 1), true).statusCode()).isEqualTo(201);
        Thread.sleep(1200);
        assertThat(send("GET", "/" + alias, null, false).statusCode()).isEqualTo(410);
        jdbc.update("INSERT INTO analytics_events(short_code,occurred_at) VALUES ('future_test','2031-01-01T00:00:00Z')");
        assertThat(jdbc.queryForObject("SELECT tableoid::regclass::text FROM analytics_events WHERE short_code='future_test'", String.class)).isEqualTo("analytics_events_default");
        jdbc.update("INSERT INTO analytics_events(short_code,occurred_at) VALUES ('retention_test',CURRENT_TIMESTAMP - INTERVAL '8 days')");
        retention.prune();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_events WHERE short_code='retention_test'", Long.class)).isZero();
    }
    @Test void invalidAndOversizeJsonDoNotReturn500() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/demo/urls"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("x".repeat(8193))).build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(413);
        request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/demo/urls"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{")).build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
    }
    @Test void no302IsAcknowledgedWhenDurableAnalyticsCannotCommit() throws Exception {
        String alias = "durable_failure";
        assertThat(send("POST", "/api/v1/urls", Map.of("originalUrl", "https://example.com", "customAlias", alias), true).statusCode()).isEqualTo(201);
        jdbc.execute("ALTER TABLE analytics_events RENAME TO analytics_events_unavailable");
        try {
            assertThat(send("GET", "/" + alias, null, false).statusCode()).isEqualTo(503);
        } finally {
            jdbc.execute("ALTER TABLE analytics_events_unavailable RENAME TO analytics_events");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_events WHERE short_code=?", Long.class, alias)).isZero();
    }
    private HttpResponse<String> send(String method, String path, Object body, boolean auth) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type", "application/json")
            .header("X-Forwarded-For", "203.0.113." + java.util.concurrent.ThreadLocalRandom.current().nextInt(1,254));
        if (auth) builder.header("X-Management-Key", KEY);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
