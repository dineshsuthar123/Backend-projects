#!/usr/bin/env bash
# Restores into a NEW database only. Never drops or overwrites the live database.
source "$(dirname "$0")/common.sh"
lock_ops
disk_check
[[ $# == 2 && "$2" =~ ^shortener_restore_[a-z0-9_]+$ && -f "$1" ]] || {
  echo 'Usage: restore.sh BACKUP.dump shortener_restore_UNIQUE_NAME' >&2; exit 1;
}
dc exec -T shortener-db createdb -U postgres -O shortener_migration "$2"
dc exec -T shortener-db pg_restore -U postgres --role=shortener_migration --no-owner --no-acl --exit-on-error -d "$2" < "$1"
dc exec -T shortener-db psql -U postgres -d "$2" -v ON_ERROR_STOP=1 --set=restore_db="$2" <<'SQL'
REVOKE ALL ON DATABASE :"restore_db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"restore_db" TO shortener_app, shortener_migration;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO shortener_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO shortener_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO shortener_app;
REVOKE ALL ON flyway_schema_history FROM shortener_app;
REVOKE UPDATE, DELETE ON urls FROM shortener_app;
REVOKE UPDATE ON analytics_events FROM shortener_app;
REVOKE ALL ON analytics_events_legacy FROM shortener_app;
ALTER DEFAULT PRIVILEGES FOR ROLE shortener_migration IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO shortener_app;
ALTER DEFAULT PRIVILEGES FOR ROLE shortener_migration IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO shortener_app;
SQL
dc exec -T shortener-db psql -U postgres -d "$2" -v ON_ERROR_STOP=1 -c 'SELECT count(*) AS restored_mappings FROM urls;'
printf 'Restored into %s. Original database preserved. See OPERATIONS.md for the reviewed cutover.\n' "$2"
