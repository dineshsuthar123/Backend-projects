# Postman Testing Guide

Base URL:

```text
http://localhost:8081
```

Start the stack:

```powershell
docker compose up -d postgres redis redpanda
mvn spring-boot:run
```

Or run the full stack with the app, Prometheus, and Grafana:

```powershell
docker compose up --build
```

## 1. Health Check

Method:

```text
GET
```

URL:

```text
{{baseUrl}}/actuator/health
```

Expected:

```json
{
  "status": "UP"
}
```

## 2. Create Short URL

Method:

```text
POST
```

URL:

```text
{{baseUrl}}/api/v1/urls
```

Headers:

```text
Content-Type: application/json
```

Body:

```json
{
  "originalUrl": "https://spring.io/projects/spring-boot",
  "customAlias": "springboot",
  "ttlSeconds": 86400
}
```

Expected:

```text
201 Created
```

Save `shortCode` from the response.

## 3. Redirect

Method:

```text
GET
```

URL:

```text
{{baseUrl}}/{{shortCode}}
```

In Postman, disable automatic redirect following:

```text
Settings -> Automatically follow redirects -> Off
```

Expected:

```text
302 Found
Location: https://spring.io/projects/spring-boot
```

## 4. Stats, Cache, And Analytics

Method:

```text
GET
```

URL:

```text
{{baseUrl}}/api/v1/urls/{{shortCode}}/stats
```

Expected:

```json
{
  "shortCode": "springboot",
  "originalUrl": "https://spring.io/projects/spring-boot",
  "cacheHit": true,
  "redirectEvents": 1
}
```

If Redpanda is enabled, analytics may take a moment to be consumed into PostgreSQL. Re-run stats after one or two seconds.

## 5. Evict Cache For Cache-Miss Testing

Method:

```text
DELETE
```

URL:

```text
{{baseUrl}}/api/v1/urls/{{shortCode}}/cache
```

Expected:

```text
204 No Content
```

Then call the redirect once. Stats should move from `cacheHit: false` to `cacheHit: true` after the redirect warms Redis again.

## 6. TTL Expiry Test

Create a URL with a short TTL:

```json
{
  "originalUrl": "https://spring.io/projects/spring-boot",
  "ttlSeconds": 2
}
```

Wait three seconds, then call `GET /{{shortCode}}`.

Expected:

```json
{
  "status": 410,
  "code": "SHORT_URL_EXPIRED",
  "message": "Short URL has expired: <shortCode>"
}
```

## 7. Rate Limit Test

Send repeated redirect requests with the same client IP.

Header:

```text
X-Forwarded-For: 10.1.1.1
```

Expected after enough requests:

```text
429 Too Many Requests
Retry-After: <seconds>
```

Body:

```json
{
  "status": 429,
  "code": "TOO_MANY_REQUESTS",
  "message": "Rate limit exceeded"
}
```

To simulate a different client, change `X-Forwarded-For`.

## 8. Observability Check

Prometheus metrics:

```text
{{baseUrl}}/actuator/prometheus
```

Search for:

```text
url_redirect_total
url_cache_hit_total
url_cache_miss_total
url_rate_limited_total
url_analytics_events_total
url_redirect_latency_seconds
```

Every response also includes:

```text
X-Request-ID: <correlation-id>
```

## 9. k6 Benchmark Note

The default k6 script now uses a constant arrival rate and synthetic `X-Forwarded-For` values so it measures redirect latency instead of immediately exhausting one local token bucket.

Run:

```powershell
k6 run benchmarks/k6/url-shortener-redirect.js
```

To stress the rate limiter intentionally:

```powershell
k6 run benchmarks/k6/rate-limit-burst.js
```

To test cold-cache stampede protection:

```powershell
k6 run benchmarks/k6/cache-stampede.js
```
