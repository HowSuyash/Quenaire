#!/bin/bash
# Creates the database Keycloak stores accounts in.
#
# A shell script rather than plain .sql because the password has to come from the
# environment. The previous version was a .sql file with the password written into
# it as the literal `keycloak`, and Postgres does no substitution in .sql init
# scripts -- so a production deployment would have shipped that password.
#
# Runs once, on an empty data directory.
set -euo pipefail

password="${KEYCLOAK_DB_PASSWORD:-keycloak}"

if [ "$password" = "keycloak" ] && [ "${REFUSE_DEFAULT_PASSWORDS:-}" = "true" ]; then
    echo "KEYCLOAK_DB_PASSWORD is still the default; refusing to create the role" >&2
    exit 1
fi

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    DO \$\$
    BEGIN
      IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'keycloak') THEN
        CREATE ROLE keycloak LOGIN PASSWORD '${password}';
      END IF;
    END
    \$\$;

    SELECT 'CREATE DATABASE keycloak OWNER keycloak'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec

    GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
SQL

echo "keycloak database ready"
