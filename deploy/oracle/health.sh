#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
disk_check
dc ps
dc exec -T shortener-app /usr/bin/timeout 3 /bin/bash /app/health.sh
dc exec -T shortener-db pg_isready -U postgres -d "${DB_NAME:-shortener}"
dc exec -T shortener-redis redis-cli ping
curl --fail --silent --show-error --max-time 15 "https://$APP_DOMAIN/actuator/health/readiness"
docker stats --no-stream $(dc ps -q)
df -h "$ORACLE_DIR"
