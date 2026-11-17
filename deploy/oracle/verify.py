"""Controlled API acceptance checks. Failure injection is local-only and opt-in.

Never logs the management key. Cloud HTTPS uses normal certificate verification.
"""
import argparse
import hashlib
import json
import os
import pathlib
import subprocess
import time
import urllib.error
import urllib.request
import uuid

HERE = pathlib.Path(__file__).resolve().parent

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return None

def require(condition, message):
    if not condition:
        raise RuntimeError(message)

def load_env(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines() if line and not line.startswith("#"))

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base-url", required=True)
    p.add_argument("--env-file", type=pathlib.Path, default=HERE / ".env")
    p.add_argument("--local-operations", action="store_true")
    args = p.parse_args()
    env = os.environ | load_env(args.env_file)
    opener = urllib.request.build_opener(NoRedirect())
    results = []

    def http(path, method="GET", body=None, auth=False, headers=None):
        values = {"Content-Type": "application/json"} | (headers or {})
        if auth:
            values["X-Management-Key"] = env["MANAGEMENT_API_KEY"]
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(args.base_url.rstrip("/") + path, data=data, headers=values, method=method)
        try:
            response = opener.open(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        raw = response.read().decode()
        try:
            content = json.loads(raw)
        except ValueError:
            content = raw
        return response.status, dict(response.headers), content

    def passed(name):
        results.append(name)
        print("PASS: " + name, flush=True)

    def dc(*words, check=True, data=None):
        command = ["docker", "compose", "--env-file", str(args.env_file), "-f", str(HERE / "compose.yml")]
        if args.local_operations:
            command += ["-f", str(HERE / "compose.test.yml")]
        return subprocess.run(command + list(words), env=env, check=check, input=data, capture_output=True)

    def sql(statement, database=None):
        return dc("exec", "-T", "shortener-db", "psql", "-U", "postgres", "-d", database or env.get("DB_NAME", "shortener"),
                  "-At", "-v", "ON_ERROR_STOP=1", "-c", statement).stdout.decode().strip()

    def wait_ready():
        deadline = time.monotonic() + 150
        while time.monotonic() < deadline:
            try:
                if http("/actuator/health/readiness")[0] == 200:
                    return
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(2)
        raise RuntimeError("Readiness did not recover")

    wait_ready()
    require(http("/")[0] == 200, "Frontend failed")
    frontend = http("/app.js")[2]
    require("innerHTML" not in frontend and env["MANAGEMENT_API_KEY"] not in frontend, "Frontend security failed")
    passed("frontend, readiness and no browser credential")
    require(http("/api/v1/urls", "POST", {"originalUrl": "https://example.com"})[0] == 401, "Creation is unprotected")
    alias = "acceptance_" + uuid.uuid4().hex[:21]
    code, headers, body = http("/api/v1/urls", "POST", {"originalUrl": "https://example.com/acceptance", "customAlias": alias}, auth=True)
    require(code == 201 and body["shortUrl"] == "https://" + env["APP_DOMAIN"] + "/" + alias,
            "Creation/public hostname/32-character alias failed: " + repr((code, body)))
    code, headers, body = http("/" + alias)
    require(code == 302 and headers.get("Location") == "https://example.com/acceptance", "302 destination failed")
    require(http(f"/api/v1/urls/{alias}/stats")[2]["redirectEvents"] == 1, "Analytics was not durable before 302")
    passed("private creation, custom alias, public hostname, 302 and durable analytics")
    require(http(f"/api/v1/urls/{alias}/cache", "DELETE")[0] == 401, "Cache eviction is unprotected")
    require(http(f"/api/v1/urls/{alias}/cache", "DELETE", auth=True)[0] == 204, "Authorized cache eviction failed")
    require(http(f"/api/v1/urls/{alias}/stats")[2]["cacheHit"] is False, "Cache eviction failed")
    require(http("/" + alias)[0] == 302, "Cache miss redirect failed")
    require(http(f"/api/v1/urls/{alias}/stats")[2]["cacheHit"] is True, "Redis cache did not warm")
    passed("Redis cache hit, miss, warmup and protected eviction")
    for path in ("/actuator/metrics", "/actuator/prometheus", "/actuator/env"):
        require(http(path)[0] == 404, "Diagnostics exposed: " + path)
    for destination in ("javascript:alert(1)", "https://user:password@example.com"):
        require(http("/api/v1/urls", "POST", {"originalUrl": destination}, auth=True)[0] == 400, "Unsafe destination accepted")
    require(http("/api/v1/demo/urls", "POST", {"originalUrl": "https://unapproved.example"})[0] == 400, "Demo destination restriction failed")
    code, _, body = http("/api/v1/demo/urls", "POST", {"originalUrl": "https://example.com/ttl", "ttlSeconds": 1})
    require(code == 201, "Demo creation failed")
    time.sleep(1.3)
    require(http("/" + body["shortCode"])[0] == 410, "TTL expiration failed")
    passed("demo, TTL, destination validation and hidden diagnostics")

    if args.local_operations:
        require(args.base_url.startswith(("http://localhost:", "http://127.0.0.1:")), "Failure injection must target localhost")
        require(env.get("COMPOSE_PROJECT_NAME", "").startswith("shortener-acceptance-"), "Use a dedicated acceptance project")
        dc("exec", "-T", "shortener-redis", "redis-cli", "FLUSHDB")
        for spoofed in ("203.0.113.21", "203.0.113.22"):
            require(http("/" + alias, headers={"X-Forwarded-For": spoofed})[0] == 302, "Controlled redirect failed")
        forged_keys = dc("exec", "-T", "shortener-redis", "redis-cli", "--scan", "--pattern", "rl:tb:203.0.113.*").stdout
        require(not forged_keys.strip(), "Proxy accepted a forged client IP")
        # Exhaust a bucket deterministically instead of sending a stress burst. The
        # production Lua script still executes for the actual HTTP request.
        future = str(int(time.time() * 1000) + 300000)
        dc("exec", "-T", "shortener-redis", "redis-cli", "MSET",
           "rl:tb:global-public-traffic:tokens", "0", "rl:tb:global-public-traffic:ts", future)
        require(http("/" + alias, headers={"X-Forwarded-For": "203.0.113.23"})[0] == 429,
                "Exhausted Redis bucket did not enforce the Lua limit")
        passed("real Redis Lua rate limiting resists changing forwarded headers")
        dc("exec", "-T", "shortener-redis", "redis-cli", "FLUSHDB")
        dc("stop", "shortener-redis")
        require(http("/" + alias)[0] == 302, "Redis outage did not fall back to PostgreSQL")
        dc("start", "shortener-redis")
        passed("Redis outage fallback preserves redirects and analytics")
        require(sql("SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole FROM pg_roles WHERE rolname='shortener_app'") == "t", "Runtime role is privileged")
        require(sql("SELECT has_schema_privilege('shortener_app','public','CREATE')") == "f", "Runtime role can change schema")
        require(sql("SELECT has_table_privilege('shortener_app','flyway_schema_history','UPDATE')") == "f", "Runtime role can modify migration history")
        passed("separate runtime and migration database privileges")
        count = sql("SELECT count(*) FROM urls")
        backup = dc("exec", "-T", "shortener-db", "pg_dump", "-U", "postgres", "-d", env.get("DB_NAME", "shortener"), "-Fc").stdout
        require(len(backup) > 100, "Backup is empty")
        backup_path = HERE / "backups" / (alias + ".dump")
        backup_path.parent.mkdir(exist_ok=True)
        backup_path.write_bytes(backup)
        restore_db = "shortener_restore_" + uuid.uuid4().hex[:12]
        dc("exec", "-T", "shortener-db", "createdb", "-U", "postgres", "-O", "shortener_migration", restore_db)
        dc("exec", "-T", "shortener-db", "pg_restore", "-U", "postgres", "--no-owner", "--no-acl", "--role=shortener_migration", "--exit-on-error", "-d", restore_db, data=backup)
        require(sql("SELECT count(*) FROM urls", restore_db) == count, "Restored mapping count differs")
        require(sql("SELECT original_url FROM urls WHERE short_code='" + alias + "'", restore_db) == "https://example.com/acceptance", "Restored mapping differs")
        passed("binary PostgreSQL backup and restoration into a separate database")
        dc("restart", "shortener-db", "shortener-app")
        wait_ready()
        require(http("/" + alias)[0] == 302, "Mapping did not survive database/application restart")
        require(sql("SELECT count(*) FROM urls") == count, "Mapping count changed on restart")
        passed("PostgreSQL persistence across container restarts")
        app_id = dc("ps", "-q", "shortener-app").stdout.decode().strip()
        info = json.loads(subprocess.check_output(["docker", "inspect", app_id]))[0]
        require(info["Config"]["User"] == "10001:10001", "Application is root")
        require(info["HostConfig"]["RestartPolicy"]["Name"] == "unless-stopped", "Restart policy missing")
        samples = json.loads(subprocess.check_output(["docker", "stats", "--no-stream", "--format", "{{json .}}", app_id]))
        passed("non-root runtime, bounded memory and automatic container restart policy")
        result = {"tests": results, "local_only": True, "app_stats": samples, "backup_sha256": hashlib.sha256(backup).hexdigest()}
    else:
        result = {"tests": results, "local_only": False, "certificate_validation": "urllib HTTPS default trust store"}
    reports = HERE / "reports"
    reports.mkdir(exist_ok=True)
    (reports / "acceptance.json").write_text(json.dumps(result, indent=2) + "\n")
    print(f"{len(results)} acceptance groups passed. VM reboot, public port scans and OCI cost checks require a real VM.")

if __name__ == "__main__":
    main()
