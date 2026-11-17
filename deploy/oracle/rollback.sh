#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
lock_ops
previous="$(cat "$ORACLE_DIR/state/previous-image")"
docker image inspect "$previous" >/dev/null
APP_IMAGE="$previous" dc up -d --no-deps --wait --wait-timeout 180 shortener-app
python3 "$ORACLE_DIR/set-image.py" "$ENV_FILE" "$previous"
printf '%s\n' "$previous" > "$ORACLE_DIR/state/current-image"
echo 'Previous application image running; persistent volumes preserved.'
