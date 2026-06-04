# Elite Extensions

This document explains the advanced systems features added after the baseline URL shortener.

## Redis Lua Token Bucket

The rate limiter now uses a token bucket implemented as a Redis Lua script. Lua matters because refill and consume happen atomically inside Redis.

The bucket stores:

- Current token count.
- Last refill timestamp.

Each request refills based on elapsed time, checks whether one token is available, consumes it if allowed, and returns remaining tokens plus retry time.

Why this is better than fixed window:

- Smooths traffic instead of resetting at hard boundaries.
- Avoids double bursts at minute edges.
- Works across many app instances.

## Cache Stampede Protection

Cold popular URLs can cause many concurrent cache misses. Without coordination, all callers hit PostgreSQL at once.

The service now uses a Redis lock per short code:

1. First cache-miss request acquires `lock:url:{shortCode}`.
2. That request reads PostgreSQL and warms Redis.
3. Other requests wait briefly and poll Redis.
4. If the cache is warmed, they redirect without PostgreSQL.
5. If the wait expires, they fall back to PostgreSQL to preserve availability.

The lock is released with Lua so one caller cannot delete another caller's lock.

## Read Replica Routing

The datasource is now a routing datasource:

- Write transactions use `primary`.
- Read-only transactions can use configured replicas.

Redirect cache misses run under `@Transactional(readOnly = true)`, so they are eligible for replica routing. This protects the primary database from read-heavy traffic.

Configure replicas with:

```yaml
app:
  datasource:
    replica-urls:
      - jdbc:postgresql://replica-1:5432/url_shortener
      - jdbc:postgresql://replica-2:5432/url_shortener
```

## Partitioned Analytics

Analytics are migrated to a PostgreSQL range-partitioned table by `occurred_at`.

Benefits:

- Old partitions can be archived or dropped quickly.
- Recent analytics queries scan less data.
- Index maintenance is smaller per partition.

The migration creates monthly partitions plus a default partition so inserts remain safe if a future monthly partition has not been created yet.

## Durable Analytics With Redpanda

Redirect analytics can now flow through Redpanda/Kafka.

The fast path publishes an event to Kafka and returns. A background consumer persists events in batches to PostgreSQL.

This is stronger than an in-memory queue because events survive process restarts once accepted by the broker.

Local Redpanda broker:

```powershell
docker compose up -d redpanda
```

Topic:

```text
url-redirect-events
```

## Benchmarking

The k6 script in `benchmarks/k6/url-shortener-redirect.js` measures redirect latency.

Run:

```powershell
k6 run benchmarks/k6/url-shortener-redirect.js
```

Useful overrides:

```powershell
$env:VUS=100
$env:DURATION="2m"
$env:BASE_URL="http://localhost:8081"
k6 run benchmarks/k6/url-shortener-redirect.js
```

Track:

- p50: normal response latency.
- p95: elevated user-visible latency.
- p99: tail latency.
- error rate.
- throughput.

Elite backend work starts when you compare these numbers before and after each systems change.
