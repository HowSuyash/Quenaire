#!/bin/bash
# ComplyAI Database Restore Script
# Usage: ./restore.sh <backup_file> [target_db]

set -euo pipefail

# Configuration
BACKUP_FILE="${1}"
TARGET_DB="${2:-complyai}"
DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-complyai}"
DB_PASSWORD="${DB_PASSWORD}"

# Validation
if [[ -z "${BACKUP_FILE}" ]]; then
    echo "Usage: $0 <backup_file> [target_db]"
    echo "Example: $0 /backups/2024/01/15/complyai_daily_20240115_030000.sql.gz complyai_restore"
    exit 1
fi

if [[ ! -f "${BACKUP_FILE}" ]]; then
    echo "Backup file not found: ${BACKUP_FILE}"
    exit 1
fi

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" >&2
}

log "Starting restore of ${BACKUP_FILE} to database ${TARGET_DB}..."

# Check if target database exists, create if not
log "Checking if target database exists..."
if PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -tc "SELECT 1 FROM pg_database WHERE datname = '${TARGET_DB}'" | grep -q 1; then
    log "Database ${TARGET_DB} exists, dropping..."
    PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "DROP DATABASE \"${TARGET_DB}\";"
fi

log "Creating target database ${TARGET_DB}..."
PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "CREATE DATABASE \"${TARGET_DB}\";"

# Determine if backup is compressed
if [[ "${BACKUP_FILE}" == *.gz ]]; then
    log "Restoring from compressed backup..."
    if gunzip -c "${BACKUP_FILE}" | PGPASSWORD="${DB_PASSWORD}" pg_restore \
        -h "${DB_HOST}" \
        -p "${DB_PORT}" \
        -U "${DB_USER}" \
        -d "${TARGET_DB}" \
        --no-owner \
        --no-privileges \
        --clean \
        --if-exists; then
        log "Restore completed successfully"
    else
        error "Restore failed"
        exit 1
    fi
else
    log "Restoring from uncompressed backup..."
    if PGPASSWORD="${DB_PASSWORD}" pg_restore \
        -h "${DB_HOST}" \
        -p "${DB_PORT}" \
        -U "${DB_USER}" \
        -d "${TARGET_DB}" \
        --no-owner \
        --no-privileges \
        --clean \
        --if-exists \
        "${BACKUP_FILE}"; then
        log "Restore completed successfully"
    else
        error "Restore failed"
        exit 1
    fi
fi

# Run Flyway migrations if needed
log "Running Flyway migrations..."
if command -v flyway &> /dev/null; then
    flyway -url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${TARGET_DB}" \
        -user="${DB_USER}" \
        -password="${DB_PASSWORD}" \
        -locations="classpath:db/migration" \
        migrate
    log "Flyway migrations completed"
else
    log "Flyway not available, skipping migrations"
fi

log "Restore process completed successfully"