# High-Performance URL Shortener

Spring Boot 3 + Java 21 backend for a read-heavy URL shortener with Redis, PostgreSQL, Redpanda/Kafka analytics, Prometheus metrics, Grafana dashboards, and k6 benchmarks.

## Architecture

```text
Client
  |
  | POST /api/v1/urls              GET /{shortCode}
  v                                v
Spring Boot API --------------> Redis URL cache
  |                                |
  | cache miss                     | token bucket Lua rate-limit state
  v                                v
PostgreSQL primary/read replica   Redpanda analytics topic
  |                                |
  | partitioned click events <-----+
  v
Prometheus <- /actuator/prometheus <- Grafana
```

Redirect hot path:

1. `RateLimitingFilter` checks a Redis Lua token bucket.
2. `UrlService.resolve` checks Redis by short code.
3. Cache hit returns `302 Found` without touching PostgreSQL.
4. Cache miss uses `lock:url:{shortCode}` stampede coordination, then reads PostgreSQL by indexed `short_code`.
5. Analytics are published asynchronously to Redpanda. Redirect does not wait for analytics persistence.

## Features

- Create short URLs with optional custom aliases and TTL.
- Fast redirects through Redis-first resolution.
- PostgreSQL source of truth with Flyway migrations and indexes.
- Redis Lua token bucket rate limiting.
- Redis cache stampede protection with short lock TTL and double-check wait.
- Redpanda/Kafka durable analytics ingestion.
- Time-partitioned analytics tables.
- Read-replica routing hook for cache misses.
- Prometheus metrics and Grafana dashboard.
- Request ID correlation with `X-Request-ID`.
- Structured machine-readable error responses.
- Browser control plane for manual testing.
- k6 benchmark suite for p50/p95/p99 proof.

## Run

One command production-like stack:

```powershell
docker compose up --build
```

Developer mode:

```powershell
docker compose up -d postgres redis redpanda
mvn spring-boot:run
```

Services:

| Service | URL |
| --- | --- |
| API and frontend | `http://localhost:8081` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3000` |
| PostgreSQL | `127.0.0.1:15432` |
| Redis | `127.0.0.1:16379` |
| Redpanda | `127.0.0.1:19092` |

Grafana login: `admin` / `admin`.

## API

Create short URL:

```http
POST /api/v1/urls
Content-Type: application/json
```

```json
{
  "originalUrl": "https://example.com/very/long/path",
  "customAlias": "optionalAlias",
  "ttlSeconds": 86400
}
```

Redirect:

```http
GET /{shortCode}
```

Returns `302 Found` with `Location: originalUrl`.

Stats:

```http
GET /api/v1/urls/{shortCode}/stats
```

Evict cache for benchmark proof:

```http
DELETE /api/v1/urls/{shortCode}/cache
```

## Database Schema

`url_mappings`

| Column | Purpose |
| --- | --- |
| `short_code` | unique lookup key for redirects |
| `original_url` | canonical redirect target |
| `created_at` | creation timestamp |
| `expires_at` | optional TTL expiry |
| `active` | soft-disable flag |

`analytics_events` is range-partitioned by `occurred_at` with monthly partitions such as `analytics_events_2026_06`, `analytics_events_2026_07`, and `analytics_events_2026_08`.

## Redis Strategy

- URL cache key: `url:{shortCode}`.
- Rate-limit state: `rl:tb:{client}:tokens` and `rl:tb:{client}:ts`.
- Stampede lock: `lock:url:{shortCode}`.
- Cache misses acquire the lock; competing callers wait briefly and double-check Redis.
- TTL URLs cache with matching TTL so expired mappings do not survive in Redis.

## Observability

Actuator:

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

Prometheus metrics:

| Metric | Meaning |
| --- | --- |
| `url_redirect_total` | successful redirects, tagged by cache hit |
| `url_create_total` | created short URLs |
| `url_cache_hit_total` | Redis cache hits |
| `url_cache_miss_total` | Redis cache misses |
| `url_rate_limited_total` | token bucket rejections |
| `url_redirect_latency_seconds` | redirect latency histogram |
| `url_analytics_events_total` | analytics ingestion outcomes |
| `url_database_lookup_total` | PostgreSQL lookups after cache miss |

## Benchmarks

Run one scenario:

```powershell
k6 run benchmarks/k6/redirect-cache-hit.js
```

Run higher RPS:

```powershell
k6 run -e RPS=250 benchmarks/k6/redirect-cache-hit.js
```

Benchmark suite:

| Scenario | Script | Default RPS | Expected behavior |
| --- | --- | ---: | --- |
| Cache-hit redirect | `benchmarks/k6/redirect-cache-hit.js` | 80 | `0%` errors, p99 under `150ms` |
| Cache-miss redirect | `benchmarks/k6/redirect-cache-miss.js` | 100 | evicts cache, proves DB fallback |
| Create short URL | `benchmarks/k6/create-short-url.js` | 100 | writes URL mappings and warms cache |
| Rate-limit burst | `benchmarks/k6/rate-limit-burst.js` | 500 | expects `302` and intentional `429` |
| Cache stampede | `benchmarks/k6/cache-stampede.js` | shared 100 VUs | cold popular URL should produce near-one DB lookup |
| TTL expiry | `benchmarks/k6/ttl-expiry.js` | 1 iteration | expects `410 SHORT_URL_EXPIRED` |
| Analytics ingestion | `benchmarks/k6/analytics-ingestion.js` | 250 | redirects publish durable analytics events |
| Stress breaking point | `benchmarks/k6/stress-breaking-point.js` | ramps to 1500 | find saturation point |

Current verified smoke result:

| Scenario | RPS | Error Rate | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Cache-hit redirect smoke | 40 | `0.00%` | `8.80ms` | `11.34ms` | `14.18ms` |

Add real results after each run and keep the machine, JVM flags, and database state noted.

## Failure Handling

| Case | Status | Code |
| --- | ---: | --- |
| Invalid URL/body | `400` | `VALIDATION_FAILED` |
| Duplicate custom alias | `409` | `CUSTOM_ALIAS_ALREADY_EXISTS` |
| Expired short URL | `410` | `SHORT_URL_EXPIRED` |
| Missing short code | `404` | `SHORT_CODE_NOT_FOUND` |
| Rate limit exceeded | `429` | `TOO_MANY_REQUESTS` |
| Unexpected server error | `500` | `INTERNAL_SERVER_ERROR` |

Redis URL cache failures degrade to PostgreSQL reads. Redis rate-limiter failures fail open to preserve availability. Analytics queue or persistence failures are counted and logged instead of slowing redirects.

## Testing

```powershell
mvn test
mvn -DskipTests package
```

Manual testing:

- Browser control plane: `http://localhost:8081`
- Postman guide: [docs/POSTMAN_TESTING.md](docs/POSTMAN_TESTING.md)
- Teaching guide: [docs/PROJECT_TEACHING_GUIDE.md](docs/PROJECT_TEACHING_GUIDE.md)
- Elite extension notes: [docs/ELITE_EXTENSIONS.md](docs/ELITE_EXTENSIONS.md)

Recommended next test upgrade: add Testcontainers for PostgreSQL, Redis, and Redpanda so integration tests exercise real infrastructure instead of mocks.
