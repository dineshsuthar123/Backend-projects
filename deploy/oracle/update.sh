#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
lock_ops
disk_check
[[ $# == 1 ]] || { echo 'Usage: update.sh IMMUTABLE_IMAGE' >&2; exit 1; }
candidate="$(docker image inspect -f '{{.Id}}' "$1")"
[[ "$(docker image inspect -f '{{.Architecture}}' "$candidate")" == "${PLATFORM#linux/}" ]]
previous="$(docker inspect -f '{{.Image}}' "$(dc ps -q shortener-app)")"
bash "$ORACLE_DIR/backup.sh" > "$ORACLE_DIR/state/pre-update-backup"
printf '%s\n' "$previous" > "$ORACLE_DIR/state/previous-image"
if APP_IMAGE="$candidate" dc up -d --no-deps --wait --wait-timeout "${DEPLOY_WAIT_TIMEOUT:-180}" shortener-app; then
  printf '%s\n' "$candidate" > "$ORACLE_DIR/state/current-image"
  # Persist across Docker Compose recreation and host reboot; no secret is printed.
  python3 "$ORACLE_DIR/set-image.py" "$ENV_FILE" "$candidate"
else
  APP_IMAGE="$previous" dc up -d --no-deps --wait --wait-timeout 180 shortener-app
  echo 'Update failed; previous image restored. Schema changes remain forward-only.' >&2
  exit 1
fi
