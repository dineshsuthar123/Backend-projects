package com.naresh.urlshortener.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UrlShortenerIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("url_shortener")
            .withUsername("url_shortener")
            .withPassword("url_shortener");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static final RedpandaContainer redpanda = new RedpandaContainer(
            DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v24.2.9")
    );

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", redpanda::getBootstrapServers);
        registry.add("app.analytics.durable-enabled", () -> "true");
        registry.add("app.analytics.flush-delay", () -> "100ms");
        registry.add("app.rate-limit.enabled", () -> "true");
        registry.add("app.rate-limit.requests", () -> "4");
        registry.add("app.rate-limit.window", () -> "30s");
    }

    @Test
    void createShortUrlReturnsCreatedBodyAndRequestId() {
        ResponseEntity<Map> response = create("https://spring.io/projects/spring-boot", alias("create"), null, ip());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
        assertThat(response.getBody()).containsKeys("shortCode", "shortUrl", "originalUrl", "createdAt");
    }

    @Test
    void invalidUrlReturnsMachineReadableValidationError() {
        ResponseEntity<Map> response = post(Map.of("originalUrl", "ftp://not-allowed.example"), ip());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_FAILED");
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
    }

    @Test
    void duplicateAliasReturnsConflictCode() {
        String alias = alias("dup");

        assertThat(create("https://example.com/one", alias, null, ip()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> duplicate = create("https://example.com/two", alias, null, ip());

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("code", "CUSTOM_ALIAS_ALREADY_EXISTS");
    }

    @Test
    void redirectCacheHitReturnsFoundLocation() throws Exception {
        ResponseEntity<Map> created = create("https://example.com/cache-hit", alias("hit"), null, ip());

        HttpResponse<String> redirect = redirect(created.getBody().get("shortCode").toString(), ip());

        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("Location")).contains("https://example.com/cache-hit");
        assertThat(redirect.headers().firstValue("X-Request-ID")).isPresent();
    }

    @Test
    void cacheEvictionMakesStatsColdThenRedirectWarmsCacheAgain() throws Exception {
        ResponseEntity<Map> created = create("https://example.com/cache-miss", alias("miss"), null, ip());
        String shortCode = created.getBody().get("shortCode").toString();

        assertThat(deleteCache(shortCode, ip()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(stats(shortCode, ip()).getBody()).containsEntry("cacheHit", false);

        HttpResponse<String> redirect = redirect(shortCode, ip());

        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(stats(shortCode, ip()).getBody()).containsEntry("cacheHit", true);
    }

    @Test
    void ttlExpiryReturnsGoneCode() throws Exception {
        ResponseEntity<Map> created = create("https://example.com/expires", alias("ttl"), 1L, ip());
        String shortCode = created.getBody().get("shortCode").toString();

        Thread.sleep(1200);
        HttpResponse<String> response = redirect(shortCode, ip());
        Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {
        });

        assertThat(response.statusCode()).isEqualTo(410);
        assertThat(body).containsEntry("code", "SHORT_URL_EXPIRED");
    }

    @Test
    void missingShortCodeReturnsNotFoundCode() throws Exception {
        HttpResponse<String> response = redirect("nope9999", ip());
        Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {
        });

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(body).containsEntry("code", "SHORT_CODE_NOT_FOUND");
    }

    @Test
    void rateLimiterAllowsRequestsWithinCapacity() throws Exception {
        ResponseEntity<Map> created = create("https://example.com/allowed", alias("allow"), null, ip());
        String shortCode = created.getBody().get("shortCode").toString();
        String clientIp = ip();

        assertThat(redirect(shortCode, clientIp).statusCode()).isEqualTo(302);
        assertThat(redirect(shortCode, clientIp).statusCode()).isEqualTo(302);
    }

    @Test
    void rateLimiterBlocksAfterCapacityAndReturnsRetryAfter() throws Exception {
        ResponseEntity<Map> created = create("https://example.com/blocked", alias("block"), null, ip());
        String shortCode = created.getBody().get("shortCode").toString();
        String clientIp = ip();

        for (int i = 0; i < 4; i++) {
            redirect(shortCode, clientIp);
        }
        HttpResponse<String> blocked = redirect(shortCode, clientIp);
        Map<String, Object> body = objectMapper.readValue(blocked.body(), new TypeReference<>() {
        });

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
        assertThat(body).containsEntry("code", "TOO_MANY_REQUESTS");
    }

    @Test
    void analyticsEventsArePersistedThroughRedpandaConsumer() throws Exception {
        ResponseEntity<Map> created = create("https://example.com/analytics", alias("events"), null, ip());
        String shortCode = created.getBody().get("shortCode").toString();

        redirect(shortCode, ip());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(stats(shortCode, ip()).getBody().get("redirectEvents")).isEqualTo(1)
        );
    }

    @Test
    void prometheusEndpointExposesCustomMetrics() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("url_cache_hit_total", "url_rate_limited_total");
    }

    private ResponseEntity<Map> create(String originalUrl, String alias, Long ttlSeconds, String clientIp) {
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("originalUrl", originalUrl);
        if (alias != null) {
            body.put("customAlias", alias);
        }
        if (ttlSeconds != null) {
            body.put("ttlSeconds", ttlSeconds);
        }
        return post(body, clientIp);
    }

    private ResponseEntity<Map> post(Map<String, Object> body, String clientIp) {
        return restTemplate.postForEntity(baseUrl() + "/api/v1/urls", entity(body, clientIp), Map.class);
    }

    private ResponseEntity<Void> deleteCache(String shortCode, String clientIp) {
        return restTemplate.exchange(baseUrl() + "/api/v1/urls/" + shortCode + "/cache",
                org.springframework.http.HttpMethod.DELETE, entity(null, clientIp), Void.class);
    }

    private ResponseEntity<Map> stats(String shortCode, String clientIp) {
        return restTemplate.exchange(baseUrl() + "/api/v1/urls/" + shortCode + "/stats",
                org.springframework.http.HttpMethod.GET, entity(null, clientIp), Map.class);
    }

    private HttpResponse<String> redirect(String shortCode, String clientIp) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/" + shortCode))
                .header("X-Forwarded-For", clientIp)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpEntity<Object> entity(Object body, String clientIp) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", clientIp);
        return new HttpEntity<>(body, headers);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static String alias(String prefix) {
        return prefix + COUNTER.incrementAndGet();
    }

    private static String ip() {
        int value = COUNTER.incrementAndGet();
        return "10.42." + (value / 200) + "." + ((value % 200) + 20);
    }
}
