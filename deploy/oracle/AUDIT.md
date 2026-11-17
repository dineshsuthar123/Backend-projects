# Production audit

The source audit covers `pom.xml`, both existing application profiles, Docker files, all API/service/configuration/repository/filter/entity classes, Flyway V1/V2, the static frontend, tests, observability configuration, and all ten k6 scripts. The original development Compose and existing untracked deployment preparations are preserved. This Oracle package is the production entry point.

## Baseline, before application edits

At commit `a8c4bfd`, Java 21.0.9 / Maven 3.9.16:

* `mvn -B clean test`: 45 tests, 0 failures/errors, **17 skipped**. Testcontainers' Docker API default was incompatible with Docker 29.
* Repeating with Docker API 1.44: 45 tests, **11 errors**, 0 skipped. The integration application could not start because the scheduler rejected `app.analytics.flush-delay=100ms`.
* Inspection also found the Kafka producer's 1-second delivery timeout was lower than its default request timeout, and the integration HTTP client threw on the 400/409 responses its assertions expected. These surfaced after the startup blocker was removed.

`src/test/resources/docker-java.properties`, the scheduler-compatible Spring maintenance baseline, valid Kafka timeout settings, and the test HTTP client's error handling address these blockers. Production and original Kafka integration tests both execute against real services; CI rejects skipped tests.

## Findings and changes

| Area | Evidence / production resolution |
| --- | --- |
| Required services | One Spring application includes the frontend, PostgreSQL is canonical storage, Redis is an optional cache plus token bucket, and Caddy terminates HTTPS. |
| Development services | Redpanda, Grafana and Prometheus containers are excluded from Oracle. The original development stack remains available. |
| Database credentials | Production generates independent 256-bit random admin, migration and runtime credentials. No default password is accepted by production Compose. Development defaults remain local development configuration. |
| Exposure | Production publishes only Caddy TCP 80/443. Application, PostgreSQL and Redis have no host ports; data networks are internal. |
| Creation / abuse | Private management key for general creation and cache eviction; it is never shipped to the browser. Public demo supports only `example.com` and `spring.io`, TTL at most one day, 10 creations/IP/day and 100 globally/day in PostgreSQL. All creation stops at 10,000 mappings. |
| Traffic / requests | Actual request stream bounded to 8 KiB, including chunked bodies; Caddy enforces the same limit and read deadlines. Redis limits individual IP and combined traffic to 120 requests/minute each; a bounded single-process global fallback applies during Redis failure. |
| Authentication boundaries | All mutations except the explicit demo require the key. Ambiguous encoded paths/path parameters are rejected. Metrics and diagnostic endpoints are hidden at both layers. Read-only redirects and per-code statistics remain public. |
| Client address | Application code consumes only `request.getRemoteAddr()`. Tomcat trusts exactly the shared Caddy address `172.30.10.2`. Caddy replaces client forwarding headers. Tests cover forged headers; direct application connections cannot select another identity. |
| Frontend | DOM text APIs replace response/error interpolation through `innerHTML`. Same-origin generated anchor paths prevent injected link schemes. Code inputs are validated; privileged eviction and burst controls are removed. Existing CSS/layout remains. |
| URL / alias validation | Only absolute HTTP(S), without userinfo, control characters or backslashes. Reserved routes cannot become aliases. A new migration widens canonical and analytics codes to 32 characters, matching the existing API contract. No target URL is fetched by the backend. Private operators remain responsible for the destinations they publish. |
| Data integrity | PostgreSQL volume is persistent; runtime cannot perform DDL or modify Flyway history/canonical mappings. Cache warming occurs after creation commits; already expired TTLs cannot become permanent Redis entries. |
| Analytics | Oracle synchronously commits the event to PostgreSQL before returning 302, and returns 503 if durable storage fails. No production in-memory fallback or Kafka dependency. This adds a database write to each redirect, unlike the previous Kafka path. Client retries/disconnects can produce additional events; it is a redirect event count, not exactly-once unique people. |
| Retention / partitions | Seven-day event retention, deleted in batches of at most 5,000/minute. Existing June–August 2026 partitions remain; the default partition handles all other dates, verified for 2031. No monthly partition DDL is necessary for this small workload. PostgreSQL autovacuum reuses reclaimed space. V2's historical legacy copy is preserved and needs separate operator review if migrating a large existing database. |
| Logging | Docker caps each service's log files at 3 × 10 MiB. Request IDs allow only safe characters. Public metrics do not create an exposure path. |
| ARM64 / resources | Java bytecode and pinned multi-architecture PostgreSQL/Redis/Caddy/JRE images. UID 10001 app, read-only root filesystem, bounded heap/native memory, container limits, readiness/liveness and graceful shutdown. See SHARED-VM.md for budgets. |
| Updates / rollback | Backup before update, resolved local image IDs, only app recreation, previous-image fallback. Flyway is forward-only: do not roll back across incompatible schema changes. No deployment command deletes volumes. Restore creates a new database and preserves the original. |

## Dependency security and applicability

The original Spring Boot 3.3.6 dependency baseline is replaced with 3.5.16, plus explicit public patch versions for Jackson, Tomcat, Netty, PostgreSQL JDBC and Kafka compression libraries. The production OS image and Java CycloneDX SBOM are scanned separately. The SBOM scan avoids an unnecessary large Java fingerprint-database download and still scans the resolved Maven package versions. An empty scanner result is not accepted as evidence of a successful Java scan.

Base images are pinned by manifest digest and dependency versions are fixed. Maven uses a fixed archive output timestamp so identical source/toolchain/dependencies produce the same application JAR; the immutable commit-tagged container remains the release identity. Container build attestations may include build-run metadata and are not claimed to have identical digests across builders.

Two scanner findings against `spring-webmvc:6.2.19` are **not applicable to this application's executed features**, based on the publisher's stated conditions:

* [CVE-2026-47884](https://spring.io/security/cve-2026-47884/): requires XSLT view rendering under a wildcard mapping. There is no `XsltView`, XSLT resolver, XML view template or wildcard view-rendering controller here; controllers return JSON or an explicit redirect response.
* [CVE-2026-47890](https://spring.io/security/cve-2026-47890/): requires server-sent events that render HTML fragments. This application has no SSE or fragment-streaming endpoint.

The publisher rates these medium/low respectively; the scanner's upstream severity source flags them higher. `.trivyignore.yaml` scopes the applicability statements to those two CVEs and that exact package/version, **expiring 2026-12-17**. `security-check.py` also fails if the affected feature classes are introduced or the review expires. No other high/critical finding is waived. Refresh this review and dependency scan before deployment after that date. A future Spring Boot 4 migration should be a separately verified compatibility change; no paid enterprise patch repository is used.

## Benchmark scope

The k6 suite contains create, hot/cold redirects, stampede, TTL, analytics, burst and breaking-point workloads with defaults up to hundreds of requests/second. It assumes a benchmark environment; synthetic forwarded IPs no longer bypass production limits. Management calls accept the key only through the CLI environment. **Do not run these stress defaults against the portfolio VM.** No old README benchmark is an Oracle measurement. Acceptance uses small controlled traffic and deterministic Redis exhaustion, not a production burst.
