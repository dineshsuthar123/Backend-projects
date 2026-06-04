# High-Performance URL Shortener Teaching Guide

This project is a compact backend systems lab. The goal is not only to shorten URLs, but to learn the pressure points behind read-heavy, low-latency services.

## What The System Does

The service exposes two main flows:

1. Create a short URL.
2. Redirect a short URL to its original URL.

Creation is write-heavy but low volume compared with redirects. Redirects are read-heavy and latency-sensitive, so the redirect path is designed around a Redis-first lookup and minimal synchronous work.

## Core Components

Spring Boot is the HTTP application layer. It owns request validation, controllers, dependency injection, scheduled jobs, and application configuration.

PostgreSQL stores canonical URL mappings and analytics events. It is the source of truth. Redis is an acceleration layer and rate-limit state store, not the source of truth.

Flyway manages schema evolution. Every schema change should be represented as an ordered migration instead of relying on Hibernate to mutate the database.

## Redirect Hot Path

The redirect endpoint is the most important path:

1. Client calls `GET /{shortCode}`.
2. The service checks Redis for `url:{shortCode}`.
3. On a cache hit, it returns `302 Found` immediately.
4. On a cache miss, it reads PostgreSQL by indexed `short_code`.
5. The result is put back into Redis.
6. Analytics are queued asynchronously.

The important lesson: low latency often comes from removing work from the critical path, not from making every operation magically faster.

## Caching Lessons

Redis is used as a read-through cache. The database remains canonical, so Redis can be lost and rebuilt without losing URL mappings.

Good cache behavior here means:

- Cache hits avoid PostgreSQL completely.
- Cache misses repair the cache.
- Redis failures degrade to PostgreSQL reads.
- Expiring URLs use matching Redis TTLs.

## Database Lessons

The `urls.short_code` column is unique and indexed. This is essential because redirect lookup is by short code.

Analytics are indexed by `short_code` and `occurred_at`, because common questions are:

- How many redirects did this URL receive?
- When did redirects happen?
- What happened in a recent time window?

## Rate Limiting Lessons

Rate limiting protects the service from abusive or accidental bursts. A limiter should be cheap, shared across instances, and fast. Redis is a good fit because multiple application instances can coordinate through the same keys.

The first version used fixed windows. Fixed windows are simple, but allow edge bursts at window boundaries. The advanced version uses token bucket logic in Redis Lua for atomic refill and consume.

## Analytics Lessons

Analytics must not block redirects. If every redirect waited for a database insert, p99 latency would suffer under load.

The initial version uses a bounded in-memory queue and batch database writes. The advanced version adds a durable Redpanda/Kafka path so analytics can survive application restarts and traffic spikes.

## Resilience Lessons

This system intentionally fails open in some places:

- Redis cache failure falls back to PostgreSQL.
- Rate limiter failure allows requests.
- Analytics enqueue failure drops events rather than slowing redirects.

That tradeoff favors availability and low redirect latency. In a real product, you would decide these policies based on business risk.

## How To Run

```powershell
docker compose up -d postgres redis
mvn spring-boot:run
```

The API runs at `http://localhost:8081`.

Create a URL:

```powershell
Invoke-RestMethod -Method Post http://localhost:8081/api/v1/urls `
  -ContentType 'application/json' `
  -Body '{"originalUrl":"https://spring.io/projects/spring-boot"}'
```

Redirect:

```powershell
curl.exe -i http://localhost:8081/{shortCode}
```

## What To Measure

For a backend engineer aiming at elite systems skill, do not stop at "it works." Measure:

- p50 latency: normal user experience.
- p95 latency: common tail behavior.
- p99 latency: worst normal production behavior.
- throughput: requests per second before latency collapses.
- cache hit ratio: how often redirects avoid PostgreSQL.
- database pool usage: whether the service is database-bound.
- dropped analytics events: whether async buffers are overloaded.

## Next Study Path

After the baseline works, study these upgrades:

- Redis Lua token bucket rate limiting.
- Cache stampede protection.
- Read replica routing for cache misses.
- Durable analytics ingestion with Kafka or Redpanda.
- Time-partitioned analytics tables.
- k6 or wrk benchmark automation.

These features teach the difference between building a backend and engineering a backend system.
