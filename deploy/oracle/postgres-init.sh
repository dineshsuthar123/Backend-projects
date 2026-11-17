#!/usr/bin/env bash
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username postgres --dbname shortener \
  --set=app_password="$DB_APP_PASSWORD" --set=migration_password="$DB_MIGRATION_PASSWORD" <<'SQL'
CREATE ROLE shortener_app LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE shortener_migration LOGIN PASSWORD :'migration_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
REVOKE ALL ON DATABASE shortener FROM PUBLIC;
GRANT CONNECT ON DATABASE shortener TO shortener_app, shortener_migration;
ALTER SCHEMA public OWNER TO shortener_migration;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO shortener_app;
ALTER DEFAULT PRIVILEGES FOR ROLE shortener_migration IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO shortener_app;
ALTER DEFAULT PRIVILEGES FOR ROLE shortener_migration IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO shortener_app;
SQL
