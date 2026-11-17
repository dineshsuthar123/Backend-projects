#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
lock_ops
disk_check
[[ "$PLATFORM" == linux/arm64 ]] || { echo 'Production deploy requires the approved ARM64 image.' >&2; exit 1; }
docker image inspect "$APP_IMAGE" >/dev/null
[[ "$(docker image inspect -f '{{.Architecture}}' "$APP_IMAGE")" == arm64 ]]
dc config -q
proxy config -q
python3 "$ORACLE_DIR/validate-caddy.py" --env-file "$ENV_FILE"
dc up -d --wait --wait-timeout 180
proxy up -d
bash "$ORACLE_DIR/health.sh"
docker image inspect -f '{{.Id}}' "$APP_IMAGE" > "$ORACLE_DIR/state/current-image"
echo 'Deployment passed the basic health checks; run verify.py for full acceptance.'
