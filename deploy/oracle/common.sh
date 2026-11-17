#!/usr/bin/env bash
set -euo pipefail
ORACLE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$ORACLE_DIR/.env}"
[[ -f "$ENV_FILE" ]] || { echo 'Generate the private environment first.' >&2; exit 1; }
# generate-env.py emits only validated names and shell-safe values. Keep this file operator-owned.
set -a
source "$ENV_FILE"
set +a
mkdir -p "$ORACLE_DIR/state" "$ORACLE_DIR/backups"
chmod 700 "$ORACLE_DIR/state" "$ORACLE_DIR/backups"
chmod 600 "$ENV_FILE"
dc() {
  local files=(-f "$ORACLE_DIR/compose.yml")
  if [[ -n "${COMPOSE_EXTRA_FILE:-}" ]]; then files+=(-f "$COMPOSE_EXTRA_FILE"); fi
  docker compose --env-file "$ENV_FILE" "${files[@]}" "$@"
}
proxy() {
  local files=(-f "$ORACLE_DIR/compose.proxy.yml")
  if [[ -n "${PROXY_EXTRA_FILE:-}" ]]; then files+=(-f "$PROXY_EXTRA_FILE"); fi
  docker compose --env-file "$ENV_FILE" "${files[@]}" "$@"
}
lock_ops() { exec 9>"$ORACLE_DIR/state/operations.lock"; flock -n 9 || { echo 'Another deployment operation is running.' >&2; exit 1; }; }
disk_check() {
  local free_kb
  free_kb="$(df -Pk "$ORACLE_DIR" | awk 'NR==2 {print $4}')"
  [[ "$free_kb" -ge 5242880 ]] || { echo 'Less than 5 GiB free; refusing a write-heavy operation.' >&2; exit 1; }
}
