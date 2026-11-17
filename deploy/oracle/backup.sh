#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
umask 077
disk_check
target="$ORACLE_DIR/backups/shortener-$(date -u +%Y%m%dT%H%M%S)-$RANDOM.dump"
temporary="$target.partial"
trap 'rm -f -- "$temporary"' EXIT
dc exec -T shortener-db pg_dump -U postgres -d "${DB_NAME:-shortener}" -Fc > "$temporary"
[[ -s "$temporary" ]]
dc exec -T shortener-db pg_restore --list < "$temporary" >/dev/null
mv -- "$temporary" "$target"
sha256sum "$target" > "$target.sha256"
printf '%s\n' "$target"
