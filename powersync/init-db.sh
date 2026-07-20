#!/usr/bin/env bash
# Idempotent Postgres prep for PowerSync (source role + publication).
# Run against devenv Postgres after it is healthy (see devenv powersync profile).
set -euo pipefail

PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
# Devenv Postgres: localhost trust as OS user (superuser). App user cannot CREATE ROLE.
ADMIN_USER="${POWERSYNC_PG_SUPERUSER:-$(whoami)}"
REPLICATION_PASSWORD="${POWERSYNC_REPLICATION_PASSWORD:-powersync}"
PSQL="${PSQL:-psql}"

export PGHOST PGPORT
export PGUSER="$ADMIN_USER"
unset PGPASSWORD || true

echo "powersync init-db: ensuring replication role + publication on zula (as $ADMIN_USER)"

"$PSQL" -d postgres -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'powersync_role') THEN
    CREATE ROLE powersync_role WITH REPLICATION BYPASSRLS LOGIN PASSWORD '${REPLICATION_PASSWORD}';
  ELSE
    ALTER ROLE powersync_role WITH REPLICATION BYPASSRLS LOGIN PASSWORD '${REPLICATION_PASSWORD}';
  END IF;
END
\$\$;

GRANT CONNECT ON DATABASE zula TO powersync_role;
GRANT CONNECT ON DATABASE zula_powersync TO powersync_role;
SQL

"$PSQL" -d zula -v ON_ERROR_STOP=1 <<SQL
GRANT USAGE ON SCHEMA public TO powersync_role;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO powersync_role;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO powersync_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO powersync_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO powersync_role;

DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'powersync') THEN
    CREATE PUBLICATION powersync FOR ALL TABLES;
  END IF;
END
\$\$;
SQL

echo "powersync init-db: ok"
