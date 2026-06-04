# High-Performance URL Shortener

Spring Boot 3 + Java 21 backend for a read-heavy URL shortener.

## Features

- Create short URLs with optional custom aliases and TTL.
- Redirect through a Redis-first hot path.
- Persist canonical mappings in PostgreSQL.
- Redis fixed-window rate limiting.
- Bounded in-memory analytics queue.
- Background analytics batch writer.
- Flyway schema with redirect and analytics indexes.

## Run Locally

```powershell
docker compose up -d postgres redis redpanda
mvn spring-boot:run
```

PostgreSQL is exposed on host port `15432`, Redis on `16379`, and Redpanda on `19092` to avoid collisions with local services.
The API runs on `http://localhost:8081`.

Create a URL:

```powershell
Invoke-RestMethod -Method Post http://localhost:8081/api/v1/urls `
  -ContentType 'application/json' `
  -Body '{"originalUrl":"https://spring.io/projects/spring-boot"}'
```

Redirect:

```powershell
Invoke-WebRequest -MaximumRedirection 0 http://localhost:8081/{shortCode}
```

## Architecture

The redirect path is optimized for low latency:

1. `GET /{shortCode}` checks Redis.
2. Cache hit returns `302 Found` without touching PostgreSQL.
3. Cache miss reads PostgreSQL by unique indexed `short_code`.
4. The mapping is written back to Redis.
5. Analytics are offered to a bounded queue and flushed in batches by a scheduled worker.

Redis failures are handled as degraded mode:

- URL cache failure falls back to PostgreSQL.
- Rate limiter failure allows the request.

This keeps availability high while preserving the fast path when Redis is healthy.

## API

`POST /api/v1/urls`

```json
{
  "originalUrl": "https://example.com/very/long/path",
  "customAlias": "optionalAlias",
  "ttlSeconds": 86400
}
```

`GET /{shortCode}`

Returns `302 Found` with `Location: originalUrl`.

## Next Elite-Level Extensions

- Redis Lua token bucket rate limiting.
- Read-replica routing for PostgreSQL cache misses.
- Time-partitioned analytics events.
- Redpanda/Kafka durable analytics ingestion.
- k6 benchmark script with p50/p95/p99 tracking.
- Redis cache stampede protection for cold popular URLs.

See [docs/PROJECT_TEACHING_GUIDE.md](docs/PROJECT_TEACHING_GUIDE.md) and [docs/ELITE_EXTENSIONS.md](docs/ELITE_EXTENSIONS.md).
